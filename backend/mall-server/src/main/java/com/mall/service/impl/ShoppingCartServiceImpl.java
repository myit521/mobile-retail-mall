package com.mall.service.impl;

import com.mall.context.BaseContext;
import com.mall.dto.ShoppingCartDTO;
import com.mall.entity.Product;
import com.mall.entity.ShoppingCart;
import com.mall.exception.BaseException;
import com.mall.constant.MessageConstant;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ShoppingCartMapper;
import com.mall.service.ShoppingCartService;
import com.mall.service.StockService;
import com.mall.vo.ProductVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StockService stockService;

    @Override
    public void add(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        // 设置用户id
        shoppingCart.setUserId(BaseContext.getCurrentId());

        //查询是否存在
        List<ShoppingCart> list = shoppingCartMapper.selectlist(shoppingCart);

        // 校验库存是否充足
        if (shoppingCartDTO.getProductId() != null) {
            int currentNum = (list != null && !list.isEmpty()) ? list.get(0).getNumber() : 0;
            if (!stockService.checkStock(shoppingCartDTO.getProductId(), currentNum + 1)) {
                throw new BaseException(MessageConstant.STOCK_NOT_ENOUGH);
            }
        }

        if (list != null && list.size() > 0) {
            //存在数量相加
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.update(cart);
        } else {
            //不存在插入数据

            //如果添加的是商品
            if (shoppingCartDTO.getProductId() != null) {
                ProductVO product = productMapper.selectById(shoppingCartDTO.getProductId());
                shoppingCart.setName(product.getName());
                shoppingCart.setImage(product.getImage());
                shoppingCart.setAmount(product.getPrice());
            }
            //添加数据
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    @Override
    public List<ShoppingCart> list() {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        return shoppingCartMapper.selectlist(shoppingCart);
    }

    @Override
    public void clean() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    /**
     * 删除购物车数据
     *
     * @param shoppingCartDTO
     */
    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        // 设置用户id
        shoppingCart.setUserId(BaseContext.getCurrentId());
        //查询数量
        List<ShoppingCart> list = shoppingCartMapper.selectlist(shoppingCart);
        if (list != null && list.size() > 0) {
            ShoppingCart cart = list.get(0);
            if (cart.getNumber() <= 1) {
                //如果数量为1则删除
                shoppingCartMapper.deleteById(cart.getId());
            } else {
                //数量减1
                cart.setNumber(cart.getNumber() - 1);
                shoppingCartMapper.update(cart);
            }
        }
    }
}
