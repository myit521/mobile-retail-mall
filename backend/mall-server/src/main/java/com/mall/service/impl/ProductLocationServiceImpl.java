package com.mall.service.impl;

import com.mall.constant.CacheConstant;
import com.mall.dto.ProductLocationDTO;
import com.mall.entity.ProductLocation;
import com.mall.mapper.ProductLocationMapper;
import com.mall.service.ProductLocationService;
import com.mall.vo.ProductLocationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ProductLocationServiceImpl implements ProductLocationService {

    @Autowired
    private ProductLocationMapper productLocationMapper;

    /**
     * 设置商品位置（新增或更新）
     */
    @Override
    @CacheEvict(value = CacheConstant.PRODUCT_LOCATION_CACHE, key = "'product:' + #productLocationDTO.productId")
    public void saveOrUpdate(ProductLocationDTO productLocationDTO) {
        ProductLocation existing = productLocationMapper.selectByProductId(productLocationDTO.getProductId());

        if (existing == null) {
            // 新增
            ProductLocation productLocation = new ProductLocation();
            BeanUtils.copyProperties(productLocationDTO, productLocation);
            productLocation.setCreateTime(LocalDateTime.now());
            productLocation.setUpdateTime(LocalDateTime.now());
            productLocationMapper.insert(productLocation);
            log.info("新增商品位置：商品ID={}, 位置={}", productLocationDTO.getProductId(), productLocationDTO.getPositionCode());
        } else {
            // 更新
            ProductLocation productLocation = new ProductLocation();
            BeanUtils.copyProperties(productLocationDTO, productLocation);
            productLocation.setUpdateTime(LocalDateTime.now());
            productLocationMapper.update(productLocation);
            log.info("更新商品位置：商品ID={}, 位置={}", productLocationDTO.getProductId(), productLocationDTO.getPositionCode());
        }
    }

    /**
     * 根据商品ID查询位置
     */
    @Override
    @Cacheable(value = CacheConstant.PRODUCT_LOCATION_CACHE, key = "'product:' + #productId", unless = "#result == null")
    public ProductLocation getByProductId(Long productId) {
        return productLocationMapper.selectByProductId(productId);
    }

    /**
     * 根据货架编号查询商品位置列表
     */
    @Override
    public List<ProductLocationVO> listByShelfCode(String shelfCode) {
        return productLocationMapper.selectPage(shelfCode);
    }

    /**
     * 删除商品位置
     */
    @Override
    @CacheEvict(value = CacheConstant.PRODUCT_LOCATION_CACHE, key = "'product:' + #productId")
    public void deleteByProductId(Long productId) {
        productLocationMapper.deleteByProductId(productId);
        log.info("删除商品位置：商品ID={}", productId);
    }
}
