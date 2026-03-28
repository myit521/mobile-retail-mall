package com.mall.mapper;

import com.mall.entity.ProductSpec;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品规格Mapper接口
 */
@Mapper
public interface ProductSpecMapper {

    /**
     * 批量插入商品规格数据
     * @param productSpecs 商品规格列表
     */
    void insertProductSpec(List<ProductSpec> productSpecs);

    /**
     * 根据商品ID批量删除规格数据
     * @param ids 商品ID列表
     */
    void deleteByProductIds(List<Long> ids);

    /**
     * 根据商品ID查询规格列表
     * @param id 商品ID
     * @return 规格列表
     */
    @Select("select * from product_spec where product_id = #{id}")
    List<ProductSpec> selectByProductId(Long id);

    /**
     * 根据商品ID列表批量查询规格
     * @param productIds 商品ID列表
     * @return 规格列表
     */
    List<ProductSpec> selectByProductIds(List<Long> productIds);

}
