package com.mall.service;

import com.mall.vo.*;

import java.time.LocalDate;

/**
 * 报表统计服务接口
 */
public interface ReportService {
    
    /**
     * 获取数据概览
     * @return 数据概览VO
     */
    OverviewVO getOverview();
    
    /**
     * 获取商品销量排行
     * @param begin 开始日期
     * @param end 结束日期
     * @param limit 数量限制（默认10）
     * @return 销量排行VO
     */
    SalesRankingVO getSalesRanking(LocalDate begin, LocalDate end, Integer limit);
    
    /**
     * 获取营业额统计
     * @param begin 开始日期
     * @param end 结束日期
     * @return 营业额统计VO
     */
    TurnoverStatisticsVO getTurnoverStatistics(LocalDate begin, LocalDate end);
    
    /**
     * 获取订单统计
     * @param begin 开始日期
     * @param end 结束日期
     * @return 订单统计VO
     */
    OrderStatisticsReportVO getOrderStatistics(LocalDate begin, LocalDate end);
}
