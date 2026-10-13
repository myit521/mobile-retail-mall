package com.sky.order.internal.application;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.auth.api.UserService;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.OrderBusinessException;
import com.sky.inventory.api.InventoryService;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.api.PaymentCompletion;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.order.internal.OrderTransition;
import com.sky.order.internal.messaging.OrderEventPublisher;
import com.sky.order.internal.persistence.OrderDetailMapper;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.order.internal.support.OrderNumberGenerator;
import com.sky.payment.api.RefundApplicationService;
import com.sky.payment.api.VerifiedPaymentHandler;
import com.sky.payment.api.PaymentCallbackCommand;
import com.sky.observability.BusinessMetrics;
import com.sky.product.api.ShoppingCartService;
import com.sky.product.api.ProductCatalog;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.utils.WeChatPayUtil;
import com.sky.utils.WeChatPayHttpException;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderApplicationService {

    
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartService shoppingCartService;
    @Autowired
    private ProductCatalog productMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private RefundApplicationService refundService;
    @Autowired
    @Lazy
    private VerifiedPaymentHandler paymentCallbackService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private OrderEventPublisher orderEventPublisher;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private OrderNumberGenerator orderNumberGenerator;
    @Autowired(required = false)
    private BusinessMetrics metrics;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        
        // 1. 检查用户购物车是否为空
        Long userId = BaseContext.getCurrentId();
        orderMapper.lockUserForSubmit(userId);
        List<ShoppingCart> cartList = validateShoppingCart(userId);
        Map<Long, Product> products = productMapper.selectByIds(cartList.stream()
                .map(ShoppingCart::getProductId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        BigDecimal amount = BigDecimal.ZERO;
        for (ShoppingCart item : cartList) {
            Product product = products.get(item.getProductId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())
                    || product.getPrice() == null || product.getPrice().signum() <= 0
                    || item.getNumber() == null || item.getNumber() <= 0) {
                throw new OrderBusinessException("购物车商品已失效");
            }
            amount = amount.add(product.getPrice().multiply(BigDecimal.valueOf(item.getNumber())));
        }
        if (ordersSubmitDTO.getAmount() == null || amount.compareTo(ordersSubmitDTO.getAmount()) != 0) {
            throw new OrderBusinessException("订单金额已变化，请刷新购物车");
        }
        
        // 2. 创建订单
        Orders orders = createOrder(ordersSubmitDTO, userId, amount);
        orderMapper.insert(orders);
        
        // 3. 创建订单明细
        List<OrderDetail> orderDetails = createOrderDetails(cartList, products, orders.getId());
        orderDetailMapper.insertBatch(orderDetails);
        
        // 4. 扣减库存（乐观锁防超卖）
        inventoryService.deductStock(orders.getId(), orderDetails);
        
        // 5. 清空购物车
        shoppingCartService.clean();

        // 6. 发送订单超时延迟消息
        orderEventPublisher.publishOrderTimeout(orders.getNumber());
        
        // 7. 返回订单数据
        log.info("用户 {} 下单成功，订单号：{}", userId, orders.getNumber());
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 验证购物车
     */
    private List<ShoppingCart> validateShoppingCart(Long userId) {
        List<ShoppingCart> cartList = shoppingCartService.list();
        if (cartList == null || cartList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        return cartList;
    }

    /**
     * 创建订单
     */
    private Orders createOrder(OrdersSubmitDTO ordersSubmitDTO, Long userId, BigDecimal amount) {
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setAmount(amount);
        orders.setUserId(userId);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setOrderTime(LocalDateTime.now());
        orders.setNumber(orderNumberGenerator.nextOrderNumber());
        return orders;
    }

    /**
     * 创建订单明细
     */
    private List<OrderDetail> createOrderDetails(List<ShoppingCart> cartList, Map<Long, Product> products, Long orderId) {
        return cartList.stream().map(item -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(item, orderDetail);
            orderDetail.setAmount(products.get(item.getProductId()).getPrice());
            orderDetail.setOrderId(orderId);
            return orderDetail;
        }).collect(Collectors.toList());
    }

    /**
     * 历史订单查询
     *
     * @param ordersPageQueryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO) {
        Long userId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(userId);
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = (Page<Orders>) orderMapper.selectPageQuery4User(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(page.getResult())) {
            List<Orders> ordersList = page.getResult();
            // 批量获取所有订单详情
            List<Long> orderIds = ordersList.stream().map(Orders::getId).collect(Collectors.toList());
            List<OrderDetail> orderDetails = orderDetailMapper.selectByOrderIds(orderIds);

            // 将订单详情按订单ID分组
            Map<Long, List<OrderDetail>> orderDetailMap = orderDetails.stream()
                    .collect(Collectors.groupingBy(OrderDetail::getOrderId));

            // 构建OrderVO列表
            orderVOList = ordersList.stream().map(item -> {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(item, orderVO);
                orderVO.setOrderDetailList(orderDetailMap.getOrDefault(item.getId(), new ArrayList<>()));
                return orderVO;
            }).collect(Collectors.toList());
        }

        PageResult pageResult = new PageResult(page.getTotal(), orderVOList);
        log.info("历史订单查询：{}", pageResult);
        return pageResult;
    }


    /**
     * 订单详情
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    @Override
    public OrderVO show(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders orders = orderMapper.selectByIdAndUserId(id, userId);
        if(orders == null)
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        return toOrderVO(orders, userId);
    }

    @Override
    public OrderVO showForAdmin(Long id) {
        Orders orders = orderMapper.selectbyId(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return toOrderVO(orders, null);
    }

    private OrderVO toOrderVO(Orders orders, Long userId) {
        Long id = orders.getId();
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
    
        // 查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
    
        // 拼接订单菜品信息字符串
        if (orderDetailList != null && !orderDetailList.isEmpty()) {
            String orderDishes = orderDetailList.stream()
                    .map(item -> item.getName() + "*" + item.getNumber())
                    .collect(Collectors.joining(","));
            orderVO.setOrderDishes(orderDishes);
        }
    
        log.info("订单详情查询完成，orderId={}, detailCount={}, status={}, payStatus={}, userId={}",
                orderVO.getId(),
                orderDetailList == null ? 0 : orderDetailList.size(),
                orderVO.getStatus(),
                orderVO.getPayStatus(),
                userId);
        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id 订单 ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result userCancel(Long id) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders orders = orderMapper.selectByIdAndUserId(id, userId);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus().equals(Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_CANCELLED);
        }
        if (!transition(orders, userId, Orders.CANCELLED, BusinessMetrics.OrderOperation.CANCEL)) {
            return Result.success();
        }
        Orders details = Orders.builder().id(id).cancelReason("用户取消订单").build();
            
        if (orders.getPayStatus().equals(Orders.PAID)) refundService.recordIntent(orders);
        updateTransitionDetails(details);
        returnStock(id);
        log.info("用户取消订单成功，订单号：{}，userId：{}", orders.getNumber(), userId);
        return Result.success();
    }

    /**
     * 用户再次下单
     *
     * @param id 原订单 ID
     * @return 下单结果
     */
    @Override
    @Transactional
    public Result repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders orders = orderMapper.selectByIdAndUserId(id, userId);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    
        // 重置订单状态
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setOrderTime(LocalDateTime.now());
        orders.setNumber(orderNumberGenerator.nextOrderNumber());
    
        // 清除原订单的时间戳和原因字段
        orders.setCancelTime(null);
        orders.setCancelReason(null);
        orders.setRejectionReason(null);
        orders.setCheckoutTime(null);
    
        List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(id);
        if (CollectionUtils.isEmpty(orderDetailList)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    
        shoppingCartService.restore(orderDetailList);
    
        int result = 1;
        if (result > 0) {
            log.info("用户再次下单成功，新订单号：{}，userId：{}", orders.getNumber(), userId);
            return Result.success();
        }
        return Result.error(MessageConstant.ORDER_FAILED);
    }


    /**
     * 条件搜索订单
     *
     * @param ordersPageQueryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        //设置默认值，防止前端传递空值导致类型转换失败
        Integer pageNum = ordersPageQueryDTO.getPage() == null ? 1 : ordersPageQueryDTO.getPage();
        Integer pageSize = ordersPageQueryDTO.getPageSize() == null ? 10 : ordersPageQueryDTO.getPageSize();
        
        PageHelper.startPage(pageNum, pageSize);
        Page<Orders> page = (Page<Orders>) orderMapper.selectPageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            List<Long> orderIds = ordersList.stream().map(Orders::getId).collect(Collectors.toList());
            List<OrderDetail> orderDetails = orderDetailMapper.selectByOrderIds(orderIds);
            Map<Long, List<OrderDetail>> orderDetailMap = orderDetails.stream()
                    .collect(Collectors.groupingBy(OrderDetail::getOrderId));

            orderVOList = ordersList.stream().map(item -> {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(item, orderVO);
                orderVO.setOrderDetailList(orderDetailMap.getOrDefault(item.getId(), new ArrayList<>()));
                return orderVO;
            }).collect(Collectors.toList());
        }

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return 统计结果
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 待接单数量（状态：待接单）
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.PENDING_PROCESS);
        // 待处理数量（状态：待处理）
        Integer confirmed = orderMapper.countByStatus(Orders.PROCESSING);
        // 待自提数量（状态：待自提）
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.READY_FOR_PICKUP);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO 接单信息
     * @return 操作结果消息
     */
    @Override
    @Transactional
    public String confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = orderMapper.selectbyId(ordersConfirmDTO.getId());
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        
        // 校验订单状态：只有"待接单"状态的订单才能接单
        if (!orders.getStatus().equals(Orders.PENDING_PROCESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        
        // 校验支付状态：只有已支付的订单才能接单
        if (!orders.getPayStatus().equals(Orders.PAID)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_PAID);
        }

        if (transition(orders, null, Orders.PROCESSING, BusinessMetrics.OrderOperation.ACCEPT)) {
            log.info("接单成功，订单号：{}", orders.getNumber());
        }
        return MessageConstant.ORDER_CONFIRM_SUCCESS;
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO 拒单信息
     * @return 操作结果消息
     */
    @Override
    @Transactional
    public String rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orders = orderMapper.selectbyId(ordersRejectionDTO.getId());
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.PENDING_PROCESS.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (!transition(orders, null, Orders.CANCELLED, BusinessMetrics.OrderOperation.REJECT)) {
            return MessageConstant.ORDER_REJECTION_SUCCESS;
        }
        Orders details = Orders.builder().id(orders.getId())
                .rejectionReason(ordersRejectionDTO.getRejectionReason()).build();

        if (orders.getPayStatus().equals(Orders.PAID)) refundService.recordIntent(orders);
        updateTransitionDetails(details);
        returnStock(ordersRejectionDTO.getId());
        log.info("拒单成功，订单号：{}，原因：{}", orders.getNumber(), ordersRejectionDTO.getRejectionReason());
        return MessageConstant.ORDER_REJECTION_SUCCESS;
    }

    /**
     * 管理员取消订单
     *
     * @param ordersCancelDTO 取消订单信息
     * @return 操作结果消息
     */
    @Override
    @Transactional
    public String adminCancel(OrdersCancelDTO ordersCancelDTO) {
        if (ordersCancelDTO == null || ordersCancelDTO.getId() == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Orders orders = orderMapper.selectbyId(ordersCancelDTO.getId());
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus().equals(Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_CANCELLED);
        }

        if (!transition(orders, null, Orders.CANCELLED, BusinessMetrics.OrderOperation.CANCEL)) {
            return MessageConstant.ORDER_CANCELLED;
        }
        Orders details = Orders.builder().id(orders.getId())
                .cancelReason(ordersCancelDTO.getCancelReason()).build();

        if (orders.getPayStatus().equals(Orders.PAID)) refundService.recordIntent(orders);
        if (details.getCancelReason() != null || details.getPayStatus() != null) {
            updateTransitionDetails(details);
        }
        returnStock(ordersCancelDTO.getId());
        log.info("管理员取消订单成功，订单号：{}，原因：{}", orders.getNumber(), ordersCancelDTO.getCancelReason());
        return MessageConstant.ORDER_CANCELLED;
    }
    
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户 id
        Long userId = BaseContext.getCurrentId();
        User user = userService.getById(userId);
            
        // 根据订单号查询订单，获取实际金额（带用户权限校验）
        Orders orders = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
            
        // 校验订单状态
        if (!orders.getStatus().equals(Orders.PENDING_PAYMENT)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
            
        // 校验订单是否已支付
        if (orders.getPayStatus().equals(Orders.PAID)) {
            throw new OrderBusinessException("该订单已支付");
        }
    
        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                orders.getAmount(), //支付金额，使用订单实际金额
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的 openid
        );
    
        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }
    
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
    
        log.info("订单支付处理完成，orderNo={}, userId={}, amount={}", ordersPaymentDTO.getOrderNumber(), userId, orders.getAmount());
        return vo;
    }

    /**
     * 支付成功，修改订单状态（增强版，带校验）
     *
     * @param outTradeNo 商户订单号
     * @param transactionId 微信支付交易号
     * @param actualAmount 实际支付金额（单位：元）
     */
    @Transactional(rollbackFor = Exception.class)
    public void paySuccessWithValidation(String outTradeNo, String transactionId, Double actualAmount) {
        PaymentCompletion completion = applyPayment(outTradeNo, actualAmount == null ? null : BigDecimal.valueOf(actualAmount));
        if (completion != null && completion.paidEvent() != null) {
            orderEventPublisher.publishOrderPaid(completion.paidEvent());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCompletion completeVerifiedPayment(String orderNumber, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new OrderBusinessException("支付金额不一致");
        }
        return applyPayment(orderNumber, amount);
    }

    private PaymentCompletion applyPayment(String orderNumber, BigDecimal amount) {
        Orders order = orderMapper.getByNumber(orderNumber);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (amount != null && amount.compareTo(order.getAmount()) != 0) {
            throw new OrderBusinessException("支付金额不一致");
        }
        if (Orders.CANCELLED.equals(order.getStatus())) {
            return compensateCanceledPayment(order);
        }
        if (Orders.PAID.equals(order.getPayStatus())) {
            return null;
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus()) || !Orders.UN_PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (orderMapper.transition(order.getId(), null, Orders.PENDING_PAYMENT,
                Orders.PENDING_PROCESS, LocalDateTime.now()) != 1) {
            Orders current = orderMapper.selectForUpdate(order.getId(), null);
            if (metrics != null) metrics.orderConflict(BusinessMetrics.OrderOperation.PAYMENT);
            if (current != null && Orders.CANCELLED.equals(current.getStatus())) {
                return compensateCanceledPayment(current);
            }
            if (current != null && Orders.PAID.equals(current.getPayStatus())) return null;
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        updateTransitionDetails(Orders.builder().id(order.getId()).payStatus(Orders.PAID).build());
        return PaymentCompletion.paid(OrderPaidMessage.builder().orderId(order.getId())
                .orderNumber(order.getNumber()).userId(order.getUserId()).amount(order.getAmount()).build());
    }

    private PaymentCompletion compensateCanceledPayment(Orders order) {
        if (Orders.UN_PAID.equals(order.getPayStatus())) {
            updateTransitionDetails(Orders.builder().id(order.getId()).payStatus(Orders.PAID).build());
        }
        if (!Orders.REFUND.equals(order.getPayStatus())) refundService.recordIntent(order);
        return PaymentCompletion.compensatingRefund();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String outTradeNo) {
        paySuccessWithValidation(outTradeNo, null, null);
    }

    @Override
    public void cancelTimeoutOrder(String orderNumber) {
        Orders localOrder = orderMapper.getByNumber(orderNumber);
        if (localOrder != null && !Orders.PENDING_PAYMENT.equals(localOrder.getStatus())
                && !Orders.CANCELLED.equals(localOrder.getStatus())) {
            return;
        }
        JSONObject trade;
        try {
            trade = weChatPayUtil.queryTrade(orderNumber);
        } catch (WeChatPayHttpException notFound) {
            if (notFound.getStatus() == 404 && "ORDER_NOT_EXIST".equals(notFound.getCode())) {
                cancelClosedTimeoutOrder(orderNumber);
                return;
            }
            throw new IllegalStateException("Unable to verify payment before timeout cancellation", notFound);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to verify payment before timeout cancellation", failure);
        }
        if (trade == null || !orderNumber.equals(trade.getString("out_trade_no"))
                || !weChatPayUtil.merchantId().equals(trade.getString("mchid"))) {
            throw new IllegalStateException("Invalid payment query response");
        }
        String state = trade.getString("trade_state");
        if ("SUCCESS".equals(state)) {
            JSONObject amount = trade.getJSONObject("amount");
            if (!weChatPayUtil.appId().equals(trade.getString("appid"))
                    || !"CNY".equals(amount == null ? null : amount.getString("currency"))
                    || amount.getBigDecimal("total") == null
                    || trade.getString("transaction_id") == null) {
                throw new IllegalStateException("Invalid paid transaction query response");
            }
            paymentCallbackService.handle(new PaymentCallbackCommand(orderNumber,
                    trade.getString("transaction_id"), amount.getBigDecimal("total").intValueExact()));
            return;
        }
        if ("NOTPAY".equals(state)) {
            try {
                weChatPayUtil.closeTrade(orderNumber);
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to close payment before timeout cancellation", failure);
            }
        } else if (!"CLOSED".equals(state)) {
            throw new IllegalStateException("Payment state requires later timeout reconciliation: " + state);
        }
        cancelClosedTimeoutOrder(orderNumber);
    }

    private void cancelClosedTimeoutOrder(String orderNumber) {
        new TransactionTemplate(transactionManager).execute(status -> {
            cancelClosedTimeoutOrderInTransaction(orderNumber);
            return null;
        });
    }

    private void cancelClosedTimeoutOrderInTransaction(String orderNumber) {
        Orders orders = orderMapper.getByNumber(orderNumber);
        if (orders == null) {
            log.warn("订单超时取消失败，订单不存在，orderNo={}", orderNumber);
            return;
        }
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus()) || !Orders.UN_PAID.equals(orders.getPayStatus())) {
            log.info("订单超时取消跳过，当前状态非待支付，orderNo={}, status={}", orderNumber, orders.getStatus());
            return;
        }

        if (!transition(orders, null, Orders.CANCELLED, BusinessMetrics.OrderOperation.TIMEOUT)) {
            return;
        }
        updateTransitionDetails(Orders.builder().id(orders.getId())
                .cancelReason(MessageConstant.ORDER_PAYMENT_TIMEOUT).build());
        returnStock(orders.getId());
        log.info("订单超时取消成功，orderNo={}", orderNumber);
    }

    private boolean transition(Orders orders, Long ownerId, int targetStatus,
                               BusinessMetrics.OrderOperation operation) {
        requireOperationForTarget(targetStatus, operation);
        OrderTransition.requireAllowed(orders.getStatus(), targetStatus);
        if (orderMapper.transition(orders.getId(), ownerId, orders.getStatus(), targetStatus,
                LocalDateTime.now()) == 1) {
            return true;
        }
        // A locking current read sees the winner under MySQL REPEATABLE READ.
        Orders current = orderMapper.selectForUpdate(orders.getId(), ownerId);
        if (current == null || (ownerId != null && !ownerId.equals(current.getUserId()))) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Integer.valueOf(targetStatus).equals(current.getStatus())) {
            if (metrics != null) metrics.orderConflict(operation);
            return false;
        }
        if (metrics != null) metrics.orderConflict(operation);
        throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
    }

    private void requireOperationForTarget(int targetStatus, BusinessMetrics.OrderOperation operation) {
        boolean matches;
        switch (targetStatus) {
            case 2: matches = operation == BusinessMetrics.OrderOperation.PAYMENT; break;
            case 3: matches = operation == BusinessMetrics.OrderOperation.ACCEPT; break;
            case 4: matches = operation == BusinessMetrics.OrderOperation.DELIVERY; break;
            case 5: matches = operation == BusinessMetrics.OrderOperation.COMPLETE; break;
            case 6: matches = operation == BusinessMetrics.OrderOperation.CANCEL
                    || operation == BusinessMetrics.OrderOperation.REJECT
                    || operation == BusinessMetrics.OrderOperation.TIMEOUT; break;
            default: throw new IllegalArgumentException("Unknown order transition target: " + targetStatus);
        }
        if (!matches) throw new IllegalArgumentException("Order operation does not match transition target");
    }

    private void updateTransitionDetails(Orders details) {
        if (orderMapper.updateTransitionDetails(details) != 1) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    private void returnStock(Long orderId) {
        inventoryService.returnStock(orderId, orderDetailMapper.selectByOrderId(orderId));
    }

}
