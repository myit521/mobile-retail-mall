package com.mall.service;

import com.mall.dto.StockAdjustDTO;
import com.mall.dto.StockAlertPageQueryDTO;
import com.mall.dto.StockCheckRecordDTO;
import com.mall.dto.StockLogPageQueryDTO;
import com.mall.entity.OrderDetail;
import com.mall.entity.StockCheckPlan;
import com.mall.entity.StockCheckRecord;
import com.mall.result.PageResult;

import java.util.List;

public interface StockService {

    /**
     * 下单时扣减库存
     * @param orderId 订单ID
     * @param orderDetails 订单明细列表
     */
    void deductStock(Long orderId, List<OrderDetail> orderDetails);

    /**
     * 取消/拒单/超时时归还库存
     * @param orderId 订单ID
     */
    void returnStock(Long orderId);

    /**
     * 校验商品库存是否充足
     * @param productId 商品ID
     * @param quantity 需要的数量
     * @return true=库存充足
     */
    boolean checkStock(Long productId, Integer quantity);

    /**
     * 手动调整库存
     * @param stockAdjustDTO 调整信息
     */
    void adjustStock(StockAdjustDTO stockAdjustDTO);

    /**
     * 创建盘点计划
     * @param plan 盘点计划
     */
    void createCheckPlan(StockCheckPlan plan);

    /**
     * 提交盘点记录
     * @param dto 盘点记录
     */
    void submitCheckRecord(StockCheckRecordDTO dto);

    /**
     * 确认盘点记录并调整库存
     * @param recordId 盘点记录ID
     */
    void confirmCheckRecord(Long recordId);

    /**
     * 完成盘点计划
     * @param planId 盘点计划ID
     */
    void completeCheckPlan(Long planId);

    /**
     * 处理预警（已处理/已忽略）
     * @param alertId 预警ID
     * @param status 目标状态
     */
    void handleAlert(Long alertId, Integer status);

    /**
     * 分页查询库存日志
     * @param dto 查询条件
     * @return 分页结果
     */
    PageResult pageQueryLogs(StockLogPageQueryDTO dto);

    /**
     * 分页查询预警记录
     * @param dto 查询条件
     * @return 分页结果
     */
    PageResult pageQueryAlerts(StockAlertPageQueryDTO dto);

    /**
     * 查询盘点计划列表
     * @return 盘点计划列表
     */
    List<StockCheckPlan> listCheckPlans();

    /**
     * 查询盘点记录
     * @param planId 盘点计划ID
     * @return 盘点记录列表
     */
    List<StockCheckRecord> listCheckRecords(Long planId);
}
