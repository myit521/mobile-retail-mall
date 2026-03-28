package com.mall.service;

import com.mall.dto.ShoppingCartDTO;
import com.mall.entity.ShoppingCart;

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
}
