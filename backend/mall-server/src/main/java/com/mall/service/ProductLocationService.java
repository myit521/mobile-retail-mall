package com.mall.service;

import com.mall.dto.ProductLocationDTO;
import com.mall.entity.ProductLocation;
import com.mall.vo.ProductLocationVO;

import java.util.List;

public interface ProductLocationService {

    /**
     * 设置商品位置（新增或更新）
     * @param productLocationDTO
     */
    void saveOrUpdate(ProductLocationDTO productLocationDTO);

    /**
     * 根据商品ID查询位置
     * @param productId
     * @return
     */
    ProductLocation getByProductId(Long productId);

    /**
     * 根据货架编号查询商品位置列表
     * @param shelfCode
     * @return
     */
    List<ProductLocationVO> listByShelfCode(String shelfCode);

    /**
     * 删除商品位置
     * @param productId
     */
    void deleteByProductId(Long productId);
}
