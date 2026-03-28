package com.mall.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.constant.MessageConstant;
import com.mall.context.BaseContext;
import com.mall.dto.StockAdjustDTO;
import com.mall.dto.StockAlertPageQueryDTO;
import com.mall.dto.StockCheckRecordDTO;
import com.mall.dto.StockLogPageQueryDTO;
import com.mall.entity.*;
import com.mall.exception.BaseException;
import com.mall.mapper.*;
import com.mall.result.PageResult;
import com.mall.service.StockService;
import com.mall.vo.StockAlertVO;
import com.mall.vo.StockLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class StockServiceImpl implements StockService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private StockLogMapper stockLogMapper;
    @Autowired
    private StockAlertMapper stockAlertMapper;
    @Autowired
    private StockCheckPlanMapper stockCheckPlanMapper;
    @Autowired
    private StockCheckRecordMapper stockCheckRecordMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;

    /**
     * 下单时扣减库存（乐观锁防超卖）
     */
    @Override
    @Transactional
    public void deductStock(Long orderId, List<OrderDetail> orderDetails) {
        for (OrderDetail detail : orderDetails) {
            Long productId = detail.getProductId();
            Integer quantity = detail.getNumber();

            // 查询当前库存（用于日志记录）
            Product product = productMapper.selectProductById(productId);
            if (product == null) {
                throw new BaseException(MessageConstant.STOCK_PRODUCT_NOT_FOUND);
            }

            // 乐观锁扣减，WHERE stock >= quantity
            int rows = productMapper.deductStock(productId, quantity);
            if (rows == 0) {
                throw new BaseException(product.getName() + MessageConstant.STOCK_NOT_ENOUGH);
            }

            // 记录库存变动日志
            StockLog stockLog = StockLog.builder()
                    .productId(productId)
                    .changeType(StockLog.TYPE_OUT)
                    .changeQuantity(-quantity)
                    .beforeStock(product.getStock())
                    .afterStock(product.getStock() - quantity)
                    .orderId(orderId)
                    .remark("下单扣减库存")
                    .createTime(LocalDateTime.now())
                    .build();
            stockLogMapper.insert(stockLog);
        }
        log.info("订单 {} 库存扣减完成，共 {} 个商品", orderId, orderDetails.size());
    }

    /**
     * 取消/拒单/超时归还库存
     */
    @Override
    @Transactional
    public void returnStock(Long orderId) {
        List<OrderDetail> orderDetails = orderDetailMapper.selectByOrderId(orderId);
        if (orderDetails == null || orderDetails.isEmpty()) {
            log.warn("订单 {} 无明细，跳过库存归还", orderId);
            return;
        }

        for (OrderDetail detail : orderDetails) {
            Long productId = detail.getProductId();
            Integer quantity = detail.getNumber();

            Product product = productMapper.selectProductById(productId);
            if (product == null) {
                log.warn("商品 {} 不存在，跳过库存归还", productId);
                continue;
            }

            // 归还库存
            productMapper.returnStock(productId, quantity);

            // 记录库存变动日志
            StockLog stockLog = StockLog.builder()
                    .productId(productId)
                    .changeType(StockLog.TYPE_RETURN)
                    .changeQuantity(quantity)
                    .beforeStock(product.getStock())
                    .afterStock(product.getStock() + quantity)
                    .orderId(orderId)
                    .remark("订单取消归还库存")
                    .createTime(LocalDateTime.now())
                    .build();
            stockLogMapper.insert(stockLog);
        }
        log.info("订单 {} 库存归还完成", orderId);
    }

    /**
     * 校验库存是否充足
     */
    @Override
    public boolean checkStock(Long productId, Integer quantity) {
        Product product = productMapper.selectProductById(productId);
        if (product == null) {
            return false;
        }
        return product.getStock() >= quantity;
    }

    /**
     * 手动调整库存
     */
    @Override
    @Transactional
    public void adjustStock(StockAdjustDTO stockAdjustDTO) {
        Long productId = stockAdjustDTO.getProductId();
        Integer quantity = stockAdjustDTO.getQuantity();

        Product product = productMapper.selectProductById(productId);
        if (product == null) {
            throw new BaseException(MessageConstant.STOCK_PRODUCT_NOT_FOUND);
        }

        int newStock = product.getStock() + quantity;
        if (newStock < 0) {
            throw new BaseException("调整后库存不能为负数");
        }

        // 更新库存
        Product update = Product.builder()
                .id(productId)
                .stock(newStock)
                .build();
        productMapper.updateProduct(update);

        // 记录日志
        StockLog stockLog = StockLog.builder()
                .productId(productId)
                .changeType(StockLog.TYPE_ADJUST)
                .changeQuantity(quantity)
                .beforeStock(product.getStock())
                .afterStock(newStock)
                .operatorId(BaseContext.getCurrentId())
                .remark(stockAdjustDTO.getRemark())
                .createTime(LocalDateTime.now())
                .build();
        stockLogMapper.insert(stockLog);

        log.info("手动调整库存：商品{}，调整量{}，当前库存{}", productId, quantity, newStock);
    }

    /**
     * 创建盘点计划
     */
    @Override
    public void createCheckPlan(StockCheckPlan plan) {
        plan.setStatus(StockCheckPlan.STATUS_PENDING);
        plan.setCreateUser(BaseContext.getCurrentId());
        plan.setCreateTime(LocalDateTime.now());
        stockCheckPlanMapper.insert(plan);
    }

    /**
     * 提交盘点记录
     */
    @Override
    @Transactional
    public void submitCheckRecord(StockCheckRecordDTO dto) {
        StockCheckPlan plan = stockCheckPlanMapper.selectById(dto.getPlanId());
        if (plan == null) {
            throw new BaseException("盘点计划不存在");
        }

        Product product = productMapper.selectProductById(dto.getProductId());
        if (product == null) {
            throw new BaseException(MessageConstant.STOCK_PRODUCT_NOT_FOUND);
        }

        // 将计划状态更新为进行中
        if (plan.getStatus().equals(StockCheckPlan.STATUS_PENDING)) {
            StockCheckPlan updatePlan = StockCheckPlan.builder()
                    .id(plan.getId())
                    .status(StockCheckPlan.STATUS_IN_PROGRESS)
                    .build();
            stockCheckPlanMapper.update(updatePlan);
        }

        StockCheckRecord record = StockCheckRecord.builder()
                .planId(dto.getPlanId())
                .productId(dto.getProductId())
                .systemStock(product.getStock())
                .actualStock(dto.getActualStock())
                .diffQuantity(dto.getActualStock() - product.getStock())
                .status(StockCheckRecord.STATUS_PENDING)
                .createTime(LocalDateTime.now())
                .build();
        stockCheckRecordMapper.insert(record);
    }

    /**
     * 确认盘点记录并调整库存
     */
    @Override
    @Transactional
    public void confirmCheckRecord(Long recordId) {
        StockCheckRecord record = stockCheckRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BaseException("盘点记录不存在");
        }
        if (!record.getStatus().equals(StockCheckRecord.STATUS_PENDING)) {
            throw new BaseException("盘点记录已处理");
        }

        Product product = productMapper.selectProductById(record.getProductId());
        if (product == null) {
            throw new BaseException(MessageConstant.STOCK_PRODUCT_NOT_FOUND);
        }

        int diff = record.getDiffQuantity();
        if (diff != 0) {
            // 调整库存到实盘数量
            Product update = Product.builder()
                    .id(record.getProductId())
                    .stock(record.getActualStock())
                    .build();
            productMapper.updateProduct(update);

            // 记录库存变动日志
            StockLog stockLog = StockLog.builder()
                    .productId(record.getProductId())
                    .changeType(StockLog.TYPE_CHECK)
                    .changeQuantity(diff)
                    .beforeStock(product.getStock())
                    .afterStock(record.getActualStock())
                    .operatorId(BaseContext.getCurrentId())
                    .remark("盘点调整")
                    .createTime(LocalDateTime.now())
                    .build();
            stockLogMapper.insert(stockLog);
        }

        // 更新记录状态
        StockCheckRecord updateRecord = StockCheckRecord.builder()
                .id(recordId)
                .status(StockCheckRecord.STATUS_ADJUSTED)
                .updateTime(LocalDateTime.now())
                .build();
        stockCheckRecordMapper.update(updateRecord);
    }

    /**
     * 完成盘点计划
     */
    @Override
    public void completeCheckPlan(Long planId) {
        StockCheckPlan plan = stockCheckPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BaseException("盘点计划不存在");
        }

        StockCheckPlan update = StockCheckPlan.builder()
                .id(planId)
                .status(StockCheckPlan.STATUS_COMPLETED)
                .completeTime(LocalDateTime.now())
                .build();
        stockCheckPlanMapper.update(update);
    }

    /**
     * 处理预警
     */
    @Override
    public void handleAlert(Long alertId, Integer status) {
        StockAlert update = StockAlert.builder()
                .id(alertId)
                .alertStatus(status)
                .handleTime(LocalDateTime.now())
                .handleUser(BaseContext.getCurrentId())
                .build();
        stockAlertMapper.update(update);
    }

    /**
     * 分页查询库存日志
     */
    @Override
    public PageResult pageQueryLogs(StockLogPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<StockLogVO> page = (Page<StockLogVO>) stockLogMapper.selectPage(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 分页查询预警记录
     */
    @Override
    public PageResult pageQueryAlerts(StockAlertPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<StockAlertVO> page = (Page<StockAlertVO>) stockAlertMapper.selectPage(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 查询盘点计划列表
     */
    @Override
    public List<StockCheckPlan> listCheckPlans() {
        return stockCheckPlanMapper.selectAll();
    }

    /**
     * 查询盘点记录
     */
    @Override
    public List<StockCheckRecord> listCheckRecords(Long planId) {
        return stockCheckRecordMapper.selectByPlanId(planId);
    }
}
