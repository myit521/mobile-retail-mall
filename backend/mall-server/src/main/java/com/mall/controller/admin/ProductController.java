package com.mall.controller.admin;

import com.mall.dto.ProductDTO;
import com.mall.dto.ProductPageQueryDTO;
import com.mall.entity.Product;
import com.mall.annotation.AdminPermission;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.ProductService;
import com.mall.vo.ProductVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product")
@Api(tags = "商品相关接口")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 新增商品
     * @param productDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增商品")
    public Result<Long> save(@RequestBody ProductDTO productDTO){
        log.info("新增商品，参数：{}",productDTO);
        Long productId = productService.save(productDTO);
        return Result.success(productId);
    }
    /*
     * 商品管理分页查询
     * @param productPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("商品管理分页查询")
    public Result<PageResult> page(ProductPageQueryDTO productPageQueryDTO){
        log.info("商品管理分页查询，参数：{}",productPageQueryDTO);
        return Result.success(productService.page(productPageQueryDTO));
    }
    /**
     * 商品批量删除
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("商品批量删除")
    @AdminPermission({"ADMIN"})
    public Result delete(@RequestParam List<Long> ids){
        log.info("商品批量删除，ids：{}",ids);
        productService.delete(ids);
        return Result.success();
    }
    /**
     * 根据id查询商品
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询商品")
    public Result<ProductVO> getById(@PathVariable Long id){
        log.info("根据id查询商品：{}",id);
        return Result.success(productService.getById(id));
    }

    /**
     * 根据分类id查询商品数据
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询商品数据")
    public Result<List<Product>> list(Long categoryId){
        log.info("根据分类id查询商品数据：{}",categoryId);
        return Result.success(productService.list(categoryId));
    }


    /**
     * 根据id修改商品状态
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("根据 id 修改商品状态")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id){
        log.info("根据id修改商品状态：{}",id);
        productService.startOrStop(status,id);
        return Result.success();
    }
    /**
     * 修改商品
     * @param productDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改商品")
    public Result update(@RequestBody ProductDTO productDTO){
        log.info("修改商品，参数：{}",productDTO);
        productService.update(productDTO);
        return Result.success();
    }
}
