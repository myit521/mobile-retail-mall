package com.mall.service;

import com.mall.dto.PhoneModelDTO;
import com.mall.dto.PhoneModelPageQueryDTO;
import com.mall.entity.PhoneModel;
import com.mall.result.PageResult;

import java.util.List;

public interface PhoneModelService {

    /**
     * 新增手机型号
     * @param phoneModelDTO
     */
    void save(PhoneModelDTO phoneModelDTO);

    /**
     * 分页查询手机型号
     * @param phoneModelPageQueryDTO
     * @return
     */
    PageResult page(PhoneModelPageQueryDTO phoneModelPageQueryDTO);

    /**
     * 根据ID查询手机型号
     * @param id
     * @return
     */
    PhoneModel getById(Long id);

    /**
     * 修改手机型号
     * @param phoneModelDTO
     */
    void update(PhoneModelDTO phoneModelDTO);

    /**
     * 批量删除手机型号
     * @param ids
     */
    void delete(List<Long> ids);

    /**
     * 启用/禁用手机型号
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 查询所有启用的手机型号
     * @return
     */
    List<PhoneModel> listAllEnabled();

    /**
     * 根据品牌查询手机型号
     * @param brand
     * @return
     */
    List<PhoneModel> listByBrand(String brand);

}
