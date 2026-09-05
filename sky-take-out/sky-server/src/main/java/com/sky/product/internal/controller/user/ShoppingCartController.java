package com.sky.product.internal.controller.user;

import com.sky.constant.MessageConstant;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.product.api.ShoppingCartService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/shoppingCart")
@Api(tags = "C端-购物车接口")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/add")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("添加购物车，productId={}, hasSpecInfo={}",
                shoppingCartDTO == null ? null : shoppingCartDTO.getProductId(),
                shoppingCartDTO != null && shoppingCartDTO.getSpecInfo() != null && !shoppingCartDTO.getSpecInfo().isEmpty());
        if(shoppingCartDTO == null|| (shoppingCartDTO.getProductId() == null))
            return Result.error(MessageConstant.INVALID_PARAM);
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }
    /**
     * 查看购物车
     * @return
     */
    @GetMapping("/list")
    public Result<List<ShoppingCart>> list() {
        log.info("查看购物车");
        return Result.success(shoppingCartService.list());
    }
    /**
     * 清空购物车
     * @return
     */
    @DeleteMapping("/clean")
    public Result clean() {
        log.info("清空购物车");
        shoppingCartService.clean();
        return Result.success();
    }
    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/sub")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("删除购物车中一个商品，productId={}, hasSpecInfo={}",
                shoppingCartDTO == null ? null : shoppingCartDTO.getProductId(),
                shoppingCartDTO != null && shoppingCartDTO.getSpecInfo() != null && !shoppingCartDTO.getSpecInfo().isEmpty());
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
}
