package com.mall.service.impl;

import com.mall.entity.Orders;
import com.mall.mapper.ReportMapper;
import com.mall.service.ReportService;
import com.mall.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报表统计服务实现类
 */
@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    
    @Autowired
    private ReportMapper reportMapper;
    
    /**
     * 获取数据概览
     */
    @Override
    public OverviewVO getOverview() {
        log.info("获取数据概览");
        
        // 今日时间范围
        LocalDateTime todayBegin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        
        // 今日营业额
        BigDecimal todayTurnover = reportMapper.selectTurnoverByDate(todayBegin, todayEnd);
        
        // 今日订单数
        Integer todayOrderCount = reportMapper.selectOrderCountByDate(todayBegin, todayEnd);
        
        // 今日有效订单数
        Integer todayValidOrderCount = reportMapper.selectValidOrderCountByDate(todayBegin, todayEnd);
        
        // 待处理订单数（状态2）
        Integer waitingOrderCount = reportMapper.selectOrderCountByStatus(Orders.TO_BE_CONFIRMED);
        
        // 处理中订单数（状态3）
        Integer processingOrderCount = reportMapper.selectOrderCountByStatus(Orders.CONFIRMED);
        
        // 商品总数
        Integer productCount = reportMapper.selectProductCount();
        
        // 在售商品数
        Integer onSaleProductCount = reportMapper.selectOnSaleProductCount();
        
        // 库存预警商品数
        Integer lowStockProductCount = reportMapper.selectLowStockProductCount();
        
        return OverviewVO.builder()
                .todayTurnover(todayTurnover != null ? todayTurnover : BigDecimal.ZERO)
                .todayOrderCount(todayOrderCount != null ? todayOrderCount : 0)
                .todayValidOrderCount(todayValidOrderCount != null ? todayValidOrderCount : 0)
                .waitingOrderCount(waitingOrderCount != null ? waitingOrderCount : 0)
                .processingOrderCount(processingOrderCount != null ? processingOrderCount : 0)
                .productCount(productCount != null ? productCount : 0)
                .onSaleProductCount(onSaleProductCount != null ? onSaleProductCount : 0)
                .lowStockProductCount(lowStockProductCount != null ? lowStockProductCount : 0)
                .build();
    }
    
    /**
     * 获取商品销量排行
     */
    @Override
    public SalesRankingVO getSalesRanking(LocalDate begin, LocalDate end, Integer limit) {
        log.info("获取商品销量排行，开始日期：{}，结束日期：{}，数量：{}", begin, end, limit);
        
        // 设置默认值
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        
        // 转换时间
        LocalDateTime beginTime = begin != null ? LocalDateTime.of(begin, LocalTime.MIN) : null;
        LocalDateTime endTime = end != null ? LocalDateTime.of(end, LocalTime.MAX) : null;
        
        // 查询销量排行
        List<ProductSalesVO> details = reportMapper.selectProductSalesRanking(beginTime, endTime, limit);
        
        if (details == null || details.isEmpty()) {
            return SalesRankingVO.builder()
                    .nameList(new ArrayList<>())
                    .salesList(new ArrayList<>())
                    .amountList(new ArrayList<>())
                    .details(new ArrayList<>())
                    .build();
        }
        
        // 提取名称列表、销量列表、销售额列表
        List<String> nameList = details.stream()
                .map(ProductSalesVO::getProductName)
                .collect(Collectors.toList());
        
        List<Integer> salesList = details.stream()
                .map(ProductSalesVO::getSalesCount)
                .collect(Collectors.toList());
        
        List<BigDecimal> amountList = details.stream()
                .map(ProductSalesVO::getSalesAmount)
                .collect(Collectors.toList());
        
        return SalesRankingVO.builder()
                .nameList(nameList)
                .salesList(salesList)
                .amountList(amountList)
                .details(details)
                .build();
    }
    
    /**
     * 获取营业额统计
     */
    @Override
    public TurnoverStatisticsVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        log.info("获取营业额统计，开始日期：{}，结束日期：{}", begin, end);
        
        // 设置默认值：最近7天
        if (begin == null) {
            begin = LocalDate.now().minusDays(6);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        
        List<String> dateList = new ArrayList<>();
        List<BigDecimal> turnoverList = new ArrayList<>();
        BigDecimal totalTurnover = BigDecimal.ZERO;
        
        // 遍历日期范围
        LocalDate currentDate = begin;
        while (!currentDate.isAfter(end)) {
            dateList.add(currentDate.toString());
            
            LocalDateTime dayBegin = LocalDateTime.of(currentDate, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(currentDate, LocalTime.MAX);
            
            BigDecimal turnover = reportMapper.selectTurnoverByDate(dayBegin, dayEnd);
            turnover = turnover != null ? turnover : BigDecimal.ZERO;
            
            turnoverList.add(turnover);
            totalTurnover = totalTurnover.add(turnover);
            
            currentDate = currentDate.plusDays(1);
        }
        
        return TurnoverStatisticsVO.builder()
                .dateList(dateList)
                .turnoverList(turnoverList)
                .totalTurnover(totalTurnover)
                .build();
    }
    
    /**
     * 获取订单统计
     */
    @Override
    public OrderStatisticsReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        log.info("获取订单统计，开始日期：{}，结束日期：{}", begin, end);
        
        // 设置默认值：最近7天
        if (begin == null) {
            begin = LocalDate.now().minusDays(6);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        
        List<String> dateList = new ArrayList<>();
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        int totalOrderCount = 0;
        int validOrderCount = 0;
        
        // 遍历日期范围
        LocalDate currentDate = begin;
        while (!currentDate.isAfter(end)) {
            dateList.add(currentDate.toString());
            
            LocalDateTime dayBegin = LocalDateTime.of(currentDate, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(currentDate, LocalTime.MAX);
            
            Integer orderCount = reportMapper.selectOrderCountByDate(dayBegin, dayEnd);
            orderCount = orderCount != null ? orderCount : 0;
            
            Integer validCount = reportMapper.selectValidOrderCountByDate(dayBegin, dayEnd);
            validCount = validCount != null ? validCount : 0;
            
            orderCountList.add(orderCount);
            validOrderCountList.add(validCount);
            
            totalOrderCount += orderCount;
            validOrderCount += validCount;
            
            currentDate = currentDate.plusDays(1);
        }
        
        // 计算完成率
        Double completionRate = 0.0;
        if (totalOrderCount > 0) {
            completionRate = BigDecimal.valueOf(validOrderCount)
                    .divide(BigDecimal.valueOf(totalOrderCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
        
        return OrderStatisticsReportVO.builder()
                .dateList(dateList)
                .orderCountList(orderCountList)
                .validOrderCountList(validOrderCountList)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .completionRate(completionRate)
                .build();
    }
}
