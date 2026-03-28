package com.mall.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.constant.MessageConstant;
import com.mall.context.BaseContext;
import com.mall.dto.*;
import com.mall.entity.*;
import com.mall.exception.OrderBusinessException;
import com.mall.mapper.*;
import com.mall.message.OrderPaidMessage;
import com.mall.mq.OrderEventPublisher;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.OrderService;
import com.mall.utils.OrderNumberGenerator;
import com.mall.utils.WeChatPayUtil;
import com.mall.vo.OrderPaymentVO;
import com.mall.vo.OrderStatisticsVO;
import com.mall.vo.OrderSubmitVO;
import com.mall.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private OrderEventPublisher orderEventPublisher;
    @Autowired
    private com.mall.service.StockService stockService;
    @Autowired
    private com.mall.utils.OrderNumberGenerator orderNumberGenerator;

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
        List<ShoppingCart> cartList = validateShoppingCart(userId);
        
        // 2. 创建订单
        Orders orders = createOrder(ordersSubmitDTO, userId);
        orderMapper.insert(orders);
        
        // 3. 创建订单明细
        List<OrderDetail> orderDetails = createOrderDetails(cartList, orders.getId());
        orderDetailMapper.insertBatch(orderDetails);
        
        // 4. 扣减库存（乐观锁防超卖）
        stockService.deductStock(orders.getId(), orderDetails);
        
        // 5. 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

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
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> cartList = shoppingCartMapper.selectlist(shoppingCart);
        if (cartList == null || cartList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        return cartList;
    }

    /**
     * 创建订单
     */
    private Orders createOrder(OrdersSubmitDTO ordersSubmitDTO, Long userId) {
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
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
    private List<OrderDetail> createOrderDetails(List<ShoppingCart> cartList, Long orderId) {
        return cartList.stream().map(item -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(item, orderDetail);
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
        Orders orders = orderMapper.selectByIdAndUserId(id, userId);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus().equals(Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_CANCELLED);
        }
            
        // 已支付的订单需要退款
        if (orders.getPayStatus().equals(Orders.PAID)) {
            try {
                // 调用微信退款接口
                performRefund(orders, "用户取消订单");
                log.info("用户取消订单退款成功，订单号：{}", orders.getNumber());
            } catch (Exception e) {
                log.error("用户取消订单退款失败，订单号：{}", orders.getNumber(), e);
                throw new OrderBusinessException("退款申请失败：" + e.getMessage());
            }
            orders.setPayStatus(Orders.REFUND);
        }
            
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelTime(LocalDateTime.now());
        orders.setCancelReason("用户取消订单");
            
        int result = orderMapper.update(orders);
        if (result > 0) {
            // 归还库存
            stockService.returnStock(id);
            log.info("用户取消订单成功，订单号：{}，userId：{}", orders.getNumber(), userId);
            return Result.success();
        }
        return Result.error(MessageConstant.ORDER_NOT_FOUND);
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
    
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            shoppingCart.setUserId(userId);
            shoppingCart.setProductId(orderDetail.getProductId());
            shoppingCart.setSpecInfo(orderDetail.getSpecInfo());
    
            List<ShoppingCart> existCartList = shoppingCartMapper.selectlist(shoppingCart);
            if (!CollectionUtils.isEmpty(existCartList)) {
                ShoppingCart existCart = existCartList.get(0);
                existCart.setNumber(existCart.getNumber() + orderDetail.getNumber());
                shoppingCartMapper.update(existCart);
                continue;
            }
    
            shoppingCart.setName(orderDetail.getName());
            shoppingCart.setImage(orderDetail.getImage());
            shoppingCart.setAmount(orderDetail.getAmount());
            shoppingCart.setNumber(orderDetail.getNumber());
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    
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

        orders.setStatus(Orders.PROCESSING);
        
        if (orderMapper.update(orders) > 0) {
            log.info("接单成功，订单号：{}", orders.getNumber());
            return MessageConstant.ORDER_CONFIRM_SUCCESS;
        }
        return MessageConstant.ORDER_CONFIRM_FAILED;
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

        // 根据支付状态决定是否需要退款
        if (orders.getPayStatus().equals(Orders.PAID)) {
            try {
                // 调用微信退款接口
                performRefund(orders, ordersRejectionDTO.getRejectionReason());
                log.info("拒单退款成功，订单号：{}", orders.getNumber());
            } catch (Exception e) {
                log.error("拒单退款失败，订单号：{}", orders.getNumber(), e);
                throw new OrderBusinessException("退款申请失败：" + e.getMessage());
            }
            orders.setPayStatus(Orders.REFUND);
        }
        
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        
        if (orderMapper.update(orders) > 0) {
            // 归还库存
            stockService.returnStock(ordersRejectionDTO.getId());
            log.info("拒单成功，订单号：{}，原因：{}", orders.getNumber(), ordersRejectionDTO.getRejectionReason());
            return MessageConstant.ORDER_REJECTION_SUCCESS;
        }
        return MessageConstant.ORDER_REJECTION_FAILED;
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

        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        // 根据支付状态决定是否需要退款
        if (orders.getPayStatus().equals(Orders.PAID)) {
            try {
                // 调用微信退款接口
                performRefund(orders, ordersCancelDTO.getCancelReason());
                log.info("管理员取消订单退款成功，订单号：{}", orders.getNumber());
            } catch (Exception e) {
                log.error("管理员取消订单退款失败，订单号：{}", orders.getNumber(), e);
                throw new OrderBusinessException("退款申请失败：" + e.getMessage());
            }
            orders.setPayStatus(Orders.REFUND);
        }

        if (orderMapper.update(orders) > 0) {
            // 归还库存
            stockService.returnStock(ordersCancelDTO.getId());
            log.info("管理员取消订单成功，订单号：{}，原因：{}", orders.getNumber(), ordersCancelDTO.getCancelReason());
            return MessageConstant.ORDER_CANCELLED;
        }
        throw new OrderBusinessException(MessageConstant.ORDER_CANCEL_FAILED);
    }
    
    /**
     * 执行退款操作
     *
     * @param orders 订单信息
     * @param reason 退款原因
     * @return 退款结果
     * @throws Exception 退款异常
     */
    private String performRefund(Orders orders, String reason) throws Exception {
        // 生成退款单号：RefundNo + 订单号 + 时间戳
        String outRefundNo = "R" + orders.getNumber() + System.currentTimeMillis();
        
        log.info("发起退款请求，orderNo={}, outRefundNo={}, amount={}, reasonLength={}", 
                orders.getNumber(), maskValue(outRefundNo, 6), orders.getAmount(), reason == null ? null : reason.length());
        
        // 调用微信退款接口
        String refundResult = weChatPayUtil.refund(
                orders.getNumber(),     // 商户订单号
                outRefundNo,           // 商户退款单号
                orders.getAmount(),    // 退款金额
                orders.getAmount()     // 原订单金额
        );
        
        return refundResult;
    }
    /**
     * 隐藏敏感信息
     *
     * @param value                待隐藏的字符串
     * @param keepSuffixLength     保留的后缀长度
     * @return 隐藏后的字符串
     */
    private String maskValue(String value, int keepSuffixLength) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (keepSuffixLength <= 0 || value.length() <= keepSuffixLength) {
            return "***";
        }
        return "***" + value.substring(value.length() - keepSuffixLength);
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
        User user = userMapper.getById(userId);
            
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
                "数码零售商城订单", //商品描述
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
        // 1. 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        
        if (ordersDB == null) {
            log.error("订单不存在，outTradeNo={}", outTradeNo);
            throw new OrderBusinessException("订单不存在：" + outTradeNo);
        }

        // 2. 幂等性校验：如果订单已支付，直接返回（不重复处理）
        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            log.warn("订单已支付，无需重复处理，outTradeNo={}, status={}, payStatus={}", 
                    outTradeNo, ordersDB.getStatus(), ordersDB.getPayStatus());
            return;
        }

        // 3. 校验订单状态：只有待支付的订单才能处理
        if (!ordersDB.getStatus().equals(Orders.PENDING_PAYMENT)) {
            log.error("订单状态异常，无法支付，outTradeNo={}, currentStatus={}", 
                    outTradeNo, ordersDB.getStatus());
            throw new OrderBusinessException("订单状态异常：" + ordersDB.getStatus());
        }

        // 4. 金额校验（可选）：如果传入实际金额，与订单金额对比
        if (actualAmount != null) {
            int compare = actualAmount.compareTo(ordersDB.getAmount().doubleValue());
            if (compare != 0) {
                log.error("支付金额与订单金额不一致，outTradeNo={}, orderAmount={}, payAmount={}", 
                        outTradeNo, ordersDB.getAmount(), actualAmount);
                throw new OrderBusinessException("支付金额不一致");
            }
        }

        // 5. 更新订单状态
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.PENDING_PROCESS)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        int result = orderMapper.update(orders);
        if (result <= 0) {
            log.error("更新订单状态失败，outTradeNo={}", outTradeNo);
            throw new OrderBusinessException("更新订单状态失败");
        }

        // 6. 发布支付成功事件，异步处理通知和审计
        orderEventPublisher.publishOrderPaid(OrderPaidMessage.builder()
                .orderId(ordersDB.getId())
                .orderNumber(ordersDB.getNumber())
                .userId(ordersDB.getUserId())
                .amount(ordersDB.getAmount())
                .build());

        log.info("订单支付成功，orderNo={}, orderId={}, userId={}, amount={}", 
                outTradeNo, ordersDB.getId(), ordersDB.getUserId(), ordersDB.getAmount());
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.PENDING_PROCESS)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        orderEventPublisher.publishOrderPaid(OrderPaidMessage.builder()
                .orderId(ordersDB.getId())
                .orderNumber(ordersDB.getNumber())
                .userId(ordersDB.getUserId())
                .amount(ordersDB.getAmount())
                .build());

    }

    @Override
    @Transactional
    public void cancelTimeoutOrder(String orderNumber) {
        Orders orders = orderMapper.getByNumber(orderNumber);
        if (orders == null) {
            log.warn("订单超时取消失败，订单不存在，orderNo={}", orderNumber);
            return;
        }
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus())) {
            log.info("订单超时取消跳过，当前状态非待支付，orderNo={}, status={}", orderNumber, orders.getStatus());
            return;
        }

        orders.setStatus(Orders.CANCELLED);
        orders.setCancelTime(LocalDateTime.now());
        orders.setCancelReason(MessageConstant.ORDER_PAYMENT_TIMEOUT);
        orderMapper.update(orders);
        stockService.returnStock(orders.getId());
        log.info("订单超时取消成功，orderNo={}", orderNumber);
    }

}
