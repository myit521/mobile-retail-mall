package com.mall.controller.user;

import com.mall.entity.PhoneModel;
import com.mall.result.Result;
import com.mall.service.PhoneModelService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userPhoneModelController")
@RequestMapping("/user/phoneModel")
@Slf4j
@Api(tags = "C端-手机型号查询接口")
public class PhoneModelController {

    @Autowired
    private PhoneModelService phoneModelService;

    /**
     * 查询所有启用的手机型号
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询所有启用的手机型号")
    public Result<List<PhoneModel>> list() {
        log.info("C端查询所有启用的手机型号");
        return Result.success(phoneModelService.listAllEnabled());
    }

    /**
     * 根据品牌查询手机型号
     * @param brand
     * @return
     */
    @GetMapping("/listByBrand")
    @ApiOperation("根据品牌查询手机型号")
    public Result<List<PhoneModel>> listByBrand(String brand) {
        log.info("根据品牌查询手机型号：{}", brand);
        return Result.success(phoneModelService.listByBrand(brand));
    }

}
