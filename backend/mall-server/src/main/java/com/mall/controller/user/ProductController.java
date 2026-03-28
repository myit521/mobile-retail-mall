package com.mall.controller.user;

import com.mall.constant.StatusConstant;
import com.mall.entity.Product;
import com.mall.result.Result;
import com.mall.service.ProductService;
import com.mall.vo.ProductVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userProductController")
@RequestMapping("/user/product")
@Slf4j
@Api(tags = "C端-商品浏览接口")
public class ProductController {
    @Autowired
    private ProductService productService;

    /**
     * 根据分类id查询商品
     * 缓存已移至 Service 层 @Cacheable 处理
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询商品")
    public Result<List<ProductVO>> list(Long categoryId) {
        log.info("根据分类id查询商品：{}", categoryId);
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setStatus(StatusConstant.ENABLE); // 查询起售中的商品

        List<ProductVO> list = productService.listWithFlavor(product);
        return Result.success(list);
    }

    /**
     * 根据手机型号ID查询商品
     * 缓存已移至 Service 层 @Cacheable 处理
     */
    @GetMapping("/listByPhoneModel")
    @ApiOperation("根据手机型号ID查询商品")
    public Result<List<ProductVO>> listByPhoneModel(Long phoneModelId) {
        log.info("根据手机型号ID查询商品：{}", phoneModelId);
        List<ProductVO> list = productService.listByPhoneModelId(phoneModelId);
        return Result.success(list);
    }

    @GetMapping("/search")
    @ApiOperation("根据关键字搜索商品")
    public Result<List<ProductVO>> search(String keyword) {
        log.info("根据关键字搜索商品，keyword={}", keyword);
        return Result.success(productService.search(keyword));
    }
}
