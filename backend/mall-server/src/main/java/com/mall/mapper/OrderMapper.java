package com.mall.mapper;

import com.github.pagehelper.Page;
import com.mall.dto.OrdersPageQueryDTO;
import com.mall.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    int insert(Orders orders);

    @Select("select * from orders where user_id = #{userId}")
    List<Orders> selectbyUserId(Long userId);

    @Select("select * from orders where id = #{id}")
    Orders selectbyId(Long id);

    /**
     * 根据订单 ID 和用户 ID 查询订单（用户端专用，带权限校验）
     */
    @Select("select * from orders where id = #{id} and user_id = #{userId}")
    Orders selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int update(Orders orders);

    List<Orders> selectPageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    List<Orders> selectPageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据状态统计订单数量
     */
    @Select("select count(*) from orders where status = #{status}")
    Integer countByStatus(Integer status);

    /**
     * 根据状态和订单时间查询订单
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> selectByStatusAndOrderTimeLt(@Param("status") Integer status, @Param("orderTime") LocalDateTime orderTime);
    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 根据订单号和用户 ID 查询订单（用户端专用，带权限校验）
     */
    @Select("select * from orders where number = #{orderNumber} and user_id = #{userId}")
    Orders getByNumberAndUserId(@Param("orderNumber") String orderNumber, @Param("userId") Long userId);


}
