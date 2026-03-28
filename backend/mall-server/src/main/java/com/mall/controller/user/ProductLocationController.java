package com.mall.controller.user;

import com.mall.entity.ProductLocation;
import com.mall.result.Result;
import com.mall.service.ProductLocationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userProductLocationController")
@RequestMapping("/user/product/location")
@Api(tags = "C端-商品位置查询接口")
@Slf4j
public class ProductLocationController {

    @Autowired
    private ProductLocationService productLocationService;

    /**
     * 根据商品ID查询位置
     * 用户选定商品后，点击查看位置时调用
     */
    @GetMapping("/{productId}")
    @ApiOperation("根据商品ID查询位置")
    public Result<ProductLocation> getByProductId(@PathVariable Long productId) {
        log.info("用户查询商品位置：productId={}", productId);
        ProductLocation location = productLocationService.getByProductId(productId);
        return Result.success(location);
    }
}
