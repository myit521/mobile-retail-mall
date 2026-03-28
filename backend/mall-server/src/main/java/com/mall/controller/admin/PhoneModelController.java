package com.mall.controller.admin;

import com.mall.dto.PhoneModelDTO;
import com.mall.dto.PhoneModelPageQueryDTO;
import com.mall.entity.PhoneModel;
import com.mall.annotation.AdminPermission;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.PhoneModelService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/phoneModel")
@Api(tags = "手机型号管理接口")
@Slf4j
public class PhoneModelController {

    @Autowired
    private PhoneModelService phoneModelService;

    /**
     * 新增手机型号
     * @param phoneModelDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增手机型号")
    public Result save(@RequestBody PhoneModelDTO phoneModelDTO) {
        log.info("新增手机型号：{}", phoneModelDTO);
        phoneModelService.save(phoneModelDTO);
        return Result.success();
    }

    /**
     * 手机型号分页查询
     * @param phoneModelPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("手机型号分页查询")
    public Result<PageResult> page(PhoneModelPageQueryDTO phoneModelPageQueryDTO) {
        log.info("手机型号分页查询：{}", phoneModelPageQueryDTO);
        return Result.success(phoneModelService.page(phoneModelPageQueryDTO));
    }

    /**
     * 根据ID查询手机型号
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询手机型号")
    public Result<PhoneModel> getById(@PathVariable Long id) {
        log.info("根据ID查询手机型号：{}", id);
        return Result.success(phoneModelService.getById(id));
    }

    /**
     * 修改手机型号
     * @param phoneModelDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改手机型号")
    public Result update(@RequestBody PhoneModelDTO phoneModelDTO) {
        log.info("修改手机型号：{}", phoneModelDTO);
        phoneModelService.update(phoneModelDTO);
        return Result.success();
    }

    /**
     * 批量删除手机型号
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除手机型号")
    @AdminPermission({"ADMIN"})
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除手机型号：{}", ids);
        phoneModelService.delete(ids);
        return Result.success();
    }

    /**
     * 启用/禁用手机型号
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用/禁用手机型号")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("启用/禁用手机型号：status={}, id={}", status, id);
        phoneModelService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 查询所有启用的手机型号（用于商品关联选择）
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询所有启用的手机型号")
    public Result<List<PhoneModel>> list() {
        log.info("查询所有启用的手机型号");
        return Result.success(phoneModelService.listAllEnabled());
    }

}
