package com.mall.mapper;

import com.mall.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    //批量插入订单明细数据
    void insertBatch(List<OrderDetail> orderDetailList);

    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> selectByOrderId(Long id);

    List<OrderDetail> selectByOrderIds(List<Long> orderIds);
}
