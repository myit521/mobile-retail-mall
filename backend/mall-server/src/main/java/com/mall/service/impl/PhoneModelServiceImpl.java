package com.mall.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.constant.CacheConstant;
import com.mall.constant.MessageConstant;
import com.mall.dto.PhoneModelDTO;
import com.mall.dto.PhoneModelPageQueryDTO;
import com.mall.entity.PhoneModel;
import com.mall.exception.BaseException;
import com.mall.mapper.PhoneModelMapper;
import com.mall.mapper.ProductPhoneModelMapper;
import com.mall.result.PageResult;
import com.mall.service.PhoneModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class PhoneModelServiceImpl implements PhoneModelService {

    @Autowired
    private PhoneModelMapper phoneModelMapper;

    @Autowired
    private ProductPhoneModelMapper productPhoneModelMapper;

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConstant.PHONE_MODEL_CACHE, allEntries = true),
            @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    })
    public void save(PhoneModelDTO phoneModelDTO) {
        log.info("新增手机型号：{}", phoneModelDTO);
        
        // 检查是否已存在相同的品牌和型号
        PhoneModel existingModel = phoneModelMapper.selectByBrandAndModelName(phoneModelDTO.getBrand(), phoneModelDTO.getModelName());
        if (existingModel != null) {
            throw new BaseException("该品牌和型号已存在：" + phoneModelDTO.getBrand() + "-" + phoneModelDTO.getModelName());
        }
        
        PhoneModel phoneModel = new PhoneModel();
        BeanUtils.copyProperties(phoneModelDTO, phoneModel);
        if (phoneModel.getStatus() == null) {
            phoneModel.setStatus(1);
        }
        if (phoneModel.getSort() == null) {
            phoneModel.setSort(0);
        }
        phoneModelMapper.insert(phoneModel);
    }

    @Override
    public PageResult page(PhoneModelPageQueryDTO phoneModelPageQueryDTO) {
        //设置默认值，防止前端传递空值导致类型转换失败
        Integer pageNum = phoneModelPageQueryDTO.getPage() == null ? 1 : phoneModelPageQueryDTO.getPage();
        Integer pageSize = phoneModelPageQueryDTO.getPageSize() == null ? 10 : phoneModelPageQueryDTO.getPageSize();
        
        PageHelper.startPage(pageNum, pageSize);
        Page<PhoneModel> page = (Page<PhoneModel>) phoneModelMapper.selectPage(phoneModelPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public PhoneModel getById(Long id) {
        return phoneModelMapper.selectById(id);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConstant.PHONE_MODEL_CACHE, allEntries = true),
            @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    })
    public void update(PhoneModelDTO phoneModelDTO) {
        log.info("修改手机型号：{}", phoneModelDTO);
        
        // 检查是否与其他记录重复（排除当前记录）
        PhoneModel existingModel = phoneModelMapper.selectByBrandAndModelName(phoneModelDTO.getBrand(), phoneModelDTO.getModelName());
        if (existingModel != null && !existingModel.getId().equals(phoneModelDTO.getId())) {
            throw new BaseException("该品牌和型号已存在：" + phoneModelDTO.getBrand() + "-" + phoneModelDTO.getModelName());
        }
        
        PhoneModel phoneModel = new PhoneModel();
        BeanUtils.copyProperties(phoneModelDTO, phoneModel);
        phoneModelMapper.update(phoneModel);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConstant.PHONE_MODEL_CACHE, allEntries = true),
            @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    })
    public void delete(List<Long> ids) {
        log.info("删除手机型号：{}", ids);
        // 检查是否有商品关联
        for (Long id : ids) {
            List<Long> productIds = productPhoneModelMapper.selectProductIdsByPhoneModelId(id);
            if (productIds != null && !productIds.isEmpty()) {
                throw new BaseException("手机型号已被商品关联，无法删除");
            }
        }
        phoneModelMapper.deleteByIds(ids);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConstant.PHONE_MODEL_CACHE, allEntries = true),
            @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    })
    public void startOrStop(Integer status, Long id) {
        PhoneModel phoneModel = PhoneModel.builder()
                .id(id)
                .status(status)
                .build();
        phoneModelMapper.update(phoneModel);
    }

    @Override
    @Cacheable(value = CacheConstant.PHONE_MODEL_CACHE, key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<PhoneModel> listAllEnabled() {
        return phoneModelMapper.selectAllEnabled();
    }

    @Override
    @Cacheable(value = CacheConstant.PHONE_MODEL_CACHE, key = "'brand:' + #brand", unless = "#result == null || #result.isEmpty()")
    public List<PhoneModel> listByBrand(String brand) {
        return phoneModelMapper.selectByBrand(brand);
    }
}
