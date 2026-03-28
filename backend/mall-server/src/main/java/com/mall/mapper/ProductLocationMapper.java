package com.mall.mapper;

import com.mall.entity.ProductLocation;
import com.mall.vo.ProductLocationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductLocationMapper {

    /**
     * 根据商品ID查询位置
     * @param productId
     * @return
     */
    @Select("select id, product_id, shelf_code, layer_num, position_code, remark from product_location where product_id = #{productId}")
    ProductLocation selectByProductId(Long productId);

    /**
     * 插入商品位置
     * @param productLocation
     */
    void insert(ProductLocation productLocation);

    /**
     * 更新商品位置
     * @param productLocation
     */
    void update(ProductLocation productLocation);

    /**
     * 根据商品ID删除位置
     * @param productId
     */
    @Select("delete from product_location where product_id = #{productId}")
    void deleteByProductId(Long productId);

    /**
     * 分页查询商品位置（关联商品信息）
     * @param shelfCode
     * @return
     */
    List<ProductLocationVO> selectPage(String shelfCode);
}
