package com.sky.product.api;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.entity.OrderDetail;

import java.util.List;

/**
 * 购物车服务接口
 */
public interface ShoppingCartService {
    /**
     * 添加购物车
     * @param shoppingCartDTO 购物车数据
     */
    void add(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看购物车
     * @return 购物车列表
     */
    List<ShoppingCart> list();

    /**
     * 清空购物车
     */
    void clean();

    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO 购物车数据
     */
    void sub(ShoppingCartDTO shoppingCartDTO);

    /**
     * Restore the items of a previous order into the current user's cart.
     */
    void restore(List<OrderDetail> orderDetails);
}
