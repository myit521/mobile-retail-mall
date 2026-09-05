package com.sky.inventory.internal.controller;

import com.sky.dto.StockAdjustDTO;
import com.sky.dto.StockAlertPageQueryDTO;
import com.sky.dto.StockCheckRecordDTO;
import com.sky.dto.StockLogPageQueryDTO;
import com.sky.entity.StockCheckPlan;
import com.sky.entity.StockCheckRecord;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.inventory.api.InventoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/admin/stock")
@Api(tags = "库存管理接口")
@Slf4j
public class StockController {

    @Autowired
    private InventoryService stockService;

    /**
     * 手动调整库存
     */
    @PutMapping("/adjust")
    @ApiOperation("手动调整库存")
    public Result adjust(@RequestBody @Valid StockAdjustDTO stockAdjustDTO) {
        log.info("手动调整库存：{}", stockAdjustDTO);
        stockService.adjustStock(stockAdjustDTO);
        return Result.success();
    }

    /**
     * 分页查询库存日志
     */
    @GetMapping("/logs")
    @ApiOperation("分页查询库存日志")
    public Result<PageResult> pageLogs(StockLogPageQueryDTO dto) {
        log.info("分页查询库存日志：{}", dto);
        return Result.success(stockService.pageQueryLogs(dto));
    }

    /**
     * 分页查询预警记录
     */
    @GetMapping("/alerts")
    @ApiOperation("分页查询预警记录")
    public Result<PageResult> pageAlerts(StockAlertPageQueryDTO dto) {
        log.info("分页查询预警记录：{}", dto);
        return Result.success(stockService.pageQueryAlerts(dto));
    }

    /**
     * 处理预警（已处理/已忽略）
     */
    @PutMapping("/alerts/{id}")
    @ApiOperation("处理预警")
    public Result handleAlert(@PathVariable Long id, @RequestParam Integer status) {
        log.info("处理预警，id：{}，状态：{}", id, status);
        stockService.handleAlert(id, status);
        return Result.success();
    }

    /**
     * 创建盘点计划
     */
    @PostMapping("/check/plan")
    @ApiOperation("创建盘点计划")
    public Result createCheckPlan(@RequestBody StockCheckPlan plan) {
        log.info("创建盘点计划：{}", plan);
        stockService.createCheckPlan(plan);
        return Result.success();
    }

    /**
     * 查询盘点计划列表
     */
    @GetMapping("/check/plans")
    @ApiOperation("查询盘点计划列表")
    public Result<List<StockCheckPlan>> listCheckPlans() {
        return Result.success(stockService.listCheckPlans());
    }

    /**
     * 完成盘点计划
     */
    @PutMapping("/check/plan/{id}/complete")
    @ApiOperation("完成盘点计划")
    public Result completeCheckPlan(@PathVariable Long id) {
        log.info("完成盘点计划：{}", id);
        stockService.completeCheckPlan(id);
        return Result.success();
    }

    /**
     * 提交盘点记录
     */
    @PostMapping("/check/record")
    @ApiOperation("提交盘点记录")
    public Result submitCheckRecord(@RequestBody StockCheckRecordDTO dto) {
        log.info("提交盘点记录：{}", dto);
        stockService.submitCheckRecord(dto);
        return Result.success();
    }

    /**
     * 查询盘点记录
     */
    @GetMapping("/check/records/{planId}")
    @ApiOperation("查询盘点记录")
    public Result<List<StockCheckRecord>> listCheckRecords(@PathVariable Long planId) {
        return Result.success(stockService.listCheckRecords(planId));
    }

    /**
     * 确认盘点记录并调整库存
     */
    @PutMapping("/check/record/{id}/confirm")
    @ApiOperation("确认盘点记录")
    public Result confirmCheckRecord(@PathVariable Long id) {
        log.info("确认盘点记录：{}", id);
        stockService.confirmCheckRecord(id);
        return Result.success();
    }
}
