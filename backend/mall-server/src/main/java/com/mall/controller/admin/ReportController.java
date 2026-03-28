package com.mall.controller.admin;

import com.mall.result.Result;
import com.mall.service.ReportService;
import com.mall.vo.OrderStatisticsReportVO;
import com.mall.vo.OverviewVO;
import com.mall.vo.SalesRankingVO;
import com.mall.vo.TurnoverStatisticsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 数据统计报表接口
 */
@RestController
@RequestMapping("/admin/report")
@Api(tags = "数据统计报表接口")
@Slf4j
public class ReportController {
    
    @Autowired
    private ReportService reportService;
    
    /**
     * 数据概览
     * @return 概览数据
     */
    @GetMapping("/overview")
    @ApiOperation("数据概览")
    public Result<OverviewVO> getOverview() {
        log.info("获取数据概览");
        return Result.success(reportService.getOverview());
    }
    
    /**
     * 商品销量排行
     * @param begin 开始日期
     * @param end 结束日期
     * @param limit 数量限制（默认10）
     * @return 销量排行数据
     */
    @GetMapping("/salesRanking")
    @ApiOperation("商品销量排行")
    public Result<SalesRankingVO> getSalesRanking(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        log.info("获取商品销量排行，开始日期：{}，结束日期：{}，数量：{}", begin, end, limit);
        return Result.success(reportService.getSalesRanking(begin, end, limit));
    }
    
    /**
     * 营业额统计
     * @param begin 开始日期
     * @param end 结束日期
     * @return 营业额统计数据
     */
    @GetMapping("/turnover")
    @ApiOperation("营业额统计")
    public Result<TurnoverStatisticsVO> getTurnoverStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("获取营业额统计，开始日期：{}，结束日期：{}", begin, end);
        return Result.success(reportService.getTurnoverStatistics(begin, end));
    }
    
    /**
     * 订单统计
     * @param begin 开始日期
     * @param end 结束日期
     * @return 订单统计数据
     */
    @GetMapping("/orderStatistics")
    @ApiOperation("订单统计")
    public Result<OrderStatisticsReportVO> getOrderStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("获取订单统计，开始日期：{}，结束日期：{}", begin, end);
        return Result.success(reportService.getOrderStatistics(begin, end));
    }
}
