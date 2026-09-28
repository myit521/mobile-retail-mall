package com.sky.inventory.internal.persistence;

import com.sky.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Inventory-owned access to stock fields in the product table.
 */
@Mapper
public interface InventoryProductMapper {

    @Select("select id, name, stock, alert_threshold from product where id = #{id}")
    Product selectProductById(Long id);

    @Update("update product set stock = stock - #{quantity} "
            + "where id = #{productId} and #{quantity} > 0 and stock >= #{quantity}")
    int deductIfAvailable(@Param("productId") long productId, @Param("quantity") int quantity);

    @Insert("insert ignore into stock_operation (order_id, action_key, outcome, create_time, update_time) "
            + "values (#{orderId}, #{actionKey}, 'ACCEPTED', now(), now())")
    int releaseOnce(@Param("orderId") long orderId, @Param("actionKey") String actionKey);

    @Update("update stock_operation set quantity = #{quantity}, outcome = 'APPLIED', update_time = now() "
            + "where order_id = #{orderId} and action_key = #{actionKey}")
    void completeRelease(@Param("orderId") long orderId,
                         @Param("actionKey") String actionKey,
                         @Param("quantity") int quantity);

    @Update("update product set stock = stock + #{quantity} where id = #{productId}")
    int returnStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("update product set stock = #{stock} where id = #{productId}")
    void updateStock(@Param("productId") Long productId, @Param("stock") Integer stock);

    @Select("select id, name, stock, alert_threshold from product where status = 1 and stock <= alert_threshold")
    List<Product> selectLowStock();
}
