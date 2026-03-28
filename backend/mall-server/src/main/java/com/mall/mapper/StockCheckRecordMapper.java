package com.mall.mapper;

import com.mall.entity.StockCheckRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockCheckRecordMapper {

    /**
     * 插入盘点记录
     * @param record
     */
    void insert(StockCheckRecord record);

    /**
     * 根据盘点计划ID查询记录
     * @param planId
     * @return
     */
    @Select("select id, plan_id, product_id, system_stock, actual_stock, diff_quantity, status, create_time, update_time from stock_check_record where plan_id = #{planId} order by create_time desc")
    List<StockCheckRecord> selectByPlanId(Long planId);

    /**
     * 根据ID查询盘点记录
     * @param id
     * @return
     */
    @Select("select id, plan_id, product_id, system_stock, actual_stock, diff_quantity, status, create_time, update_time from stock_check_record where id = #{id}")
    StockCheckRecord selectById(Long id);

    /**
     * 更新盘点记录状态
     * @param record
     */
    void update(StockCheckRecord record);
}
