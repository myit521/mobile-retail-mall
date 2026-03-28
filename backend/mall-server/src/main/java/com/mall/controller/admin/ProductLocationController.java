package com.mall.controller.admin;

import com.mall.dto.ProductLocationDTO;
import com.mall.entity.ProductLocation;
import com.mall.annotation.AdminPermission;
import com.mall.result.Result;
import com.mall.service.ProductLocationService;
import com.mall.vo.ProductLocationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/productLocation")
@Api(tags = "商品位置管理接口")
@Slf4j
public class ProductLocationController {

    @Autowired
    private ProductLocationService productLocationService;

    /**
     * 设置商品位置（新增或更新）
     */
    @PostMapping
    @ApiOperation("设置商品位置")
    public Result save(@RequestBody ProductLocationDTO productLocationDTO) {
        log.info("设置商品位置：{}", productLocationDTO);
        productLocationService.saveOrUpdate(productLocationDTO);
        return Result.success();
    }

    /**
     * 根据商品ID查询位置
     */
    @GetMapping("/{productId}")
    @ApiOperation("根据商品ID查询位置")
    public Result<ProductLocation> getByProductId(@PathVariable Long productId) {
        ProductLocation location = productLocationService.getByProductId(productId);
        return Result.success(location);
    }

    /**
     * 查询商品位置列表
     */
    @GetMapping("/list")
    @ApiOperation("查询商品位置列表")
    public Result<List<ProductLocationVO>> list(@RequestParam(required = false) String shelfCode) {
        List<ProductLocationVO> list = productLocationService.listByShelfCode(shelfCode);
        return Result.success(list);
    }

    /**
     * 删除商品位置
     */
    @DeleteMapping("/{productId}")
    @ApiOperation("删除商品位置")
    @AdminPermission({"ADMIN"})
    public Result delete(@PathVariable Long productId) {
        log.info("删除商品位置：productId={}", productId);
        productLocationService.deleteByProductId(productId);
        return Result.success();
    }
}
