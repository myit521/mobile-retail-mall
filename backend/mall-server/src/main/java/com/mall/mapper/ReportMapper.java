package com.mall.mapper;

import com.mall.vo.ProductSalesVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表统计Mapper
 */
@Mapper
public interface ReportMapper {
    
    /**
     * 查询指定时间范围内的商品销量排行
     * @param begin 开始时间
     * @param end 结束时间
     * @param limit 数量限制
     * @return 商品销量列表
     */
    List<ProductSalesVO> selectProductSalesRanking(@Param("begin") LocalDateTime begin, 
                                                    @Param("end") LocalDateTime end,
                                                    @Param("limit") Integer limit);
    
    /**
     * 统计指定日期的营业额（已完成订单）
     * @param begin 开始时间
     * @param end 结束时间
     * @return 营业额
     */
    BigDecimal selectTurnoverByDate(@Param("begin") LocalDateTime begin, 
                                    @Param("end") LocalDateTime end);
    
    /**
     * 统计指定日期的订单数
     * @param begin 开始时间
     * @param end 结束时间
     * @return 订单数
     */
    Integer selectOrderCountByDate(@Param("begin") LocalDateTime begin, 
                                   @Param("end") LocalDateTime end);
    
    /**
     * 统计指定日期的有效订单数（已完成）
     * @param begin 开始时间
     * @param end 结束时间
     * @return 有效订单数
     */
    Integer selectValidOrderCountByDate(@Param("begin") LocalDateTime begin, 
                                        @Param("end") LocalDateTime end);
    
    /**
     * 统计商品总数
     * @return 商品总数
     */
    Integer selectProductCount();
    
    /**
     * 统计在售商品数
     * @return 在售商品数
     */
    Integer selectOnSaleProductCount();
    
    /**
     * 统计库存预警商品数
     * @return 库存预警商品数
     */
    Integer selectLowStockProductCount();
    
    /**
     * 统计指定状态的订单数
     * @param status 订单状态
     * @return 订单数
     */
    Integer selectOrderCountByStatus(@Param("status") Integer status);
}
