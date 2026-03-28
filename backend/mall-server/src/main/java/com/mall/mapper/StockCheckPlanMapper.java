package com.mall.mapper;

import com.mall.entity.StockCheckPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockCheckPlanMapper {

    /**
     * 插入盘点计划
     * @param plan
     */
    void insert(StockCheckPlan plan);

    /**
     * 更新盘点计划
     * @param plan
     */
    void update(StockCheckPlan plan);

    /**
     * 根据ID查询盘点计划
     * @param id
     * @return
     */
    @Select("select id, plan_name, status, create_user, create_time, complete_time, remark from stock_check_plan where id = #{id}")
    StockCheckPlan selectById(Long id);

    /**
     * 查询所有盘点计划
     * @return
     */
    @Select("select id, plan_name, status, create_user, create_time, complete_time, remark from stock_check_plan order by create_time desc")
    List<StockCheckPlan> selectAll();
}
