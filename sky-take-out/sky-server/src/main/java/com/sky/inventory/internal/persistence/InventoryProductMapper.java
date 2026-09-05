package com.sky.inventory.internal.persistence;

import com.sky.entity.Product;
import org.apache.ibatis.annotations.Mapper;
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

    @Update("update product set stock = stock - #{quantity} where id = #{productId} and stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("update product set stock = stock + #{quantity} where id = #{productId}")
    void returnStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("update product set stock = #{stock} where id = #{productId}")
    void updateStock(@Param("productId") Long productId, @Param("stock") Integer stock);

    @Select("select id, name, stock, alert_threshold from product where status = 1 and stock <= alert_threshold")
    List<Product> selectLowStock();
}
