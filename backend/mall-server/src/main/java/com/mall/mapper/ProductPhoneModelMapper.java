package com.mall.mapper;

import com.mall.entity.PhoneModel;
import com.mall.entity.ProductPhoneModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductPhoneModelMapper {

    /**
     * 批量插入商品与手机型号关联
     * @param list
     */
    void insertBatch(List<ProductPhoneModel> list);

    /**
     * 根据商品ID删除关联关系
     * @param productId
     */
    @Delete("delete from product_phone_model where product_id = #{productId}")
    void deleteByProductId(Long productId);

    /**
     * 根据商品ID列表批量删除关联关系
     * @param productIds
     */
    void deleteByProductIds(List<Long> productIds);

    /**
     * 根据商品ID查询关联的手机型号
     * @param productId
     * @return
     */
    List<PhoneModel> selectPhoneModelsByProductId(Long productId);

    /**
     * 根据商品ID列表查询关联关系
     * @param productIds
     * @return
     */
    List<ProductPhoneModel> selectByProductIds(List<Long> productIds);

    /**
     * 根据手机型号ID查询关联的商品ID列表
     * @param phoneModelId
     * @return
     */
    @Select("select product_id from product_phone_model where phone_model_id = #{phoneModelId}")
    List<Long> selectProductIdsByPhoneModelId(Long phoneModelId);

    /**
     * 根据手机型号ID删除关联关系
     * @param phoneModelId
     */
    @Delete("delete from product_phone_model where phone_model_id = #{phoneModelId}")
    void deleteByPhoneModelId(Long phoneModelId);

}
