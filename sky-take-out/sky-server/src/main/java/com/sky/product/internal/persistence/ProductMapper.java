package com.sky.product.internal.persistence;

import com.sky.annotation.AutoFill;
import com.sky.dto.ProductDTO;
import com.sky.dto.ProductPageQueryDTO;
import com.sky.entity.Product;
import com.sky.enumeration.OperationType;
import com.sky.vo.ProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper {

    /**
     * 根据分类id查询商品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from product where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 插入商品数据
     * @param product
     */
    @AutoFill( OperationType.INSERT)
    void insertProduct(Product product);


    /**
     * 商品分页查询
     * @param productPageQueryDTO
     * @return
     */
    List<ProductVO> selectProductPage(ProductPageQueryDTO productPageQueryDTO);

    /**
     * 批量删除商品
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据id查询商品和对应的规格数据
     * @param id
     * @return
     */
    ProductVO selectById(Long id);

    /**
     * 根据分类id查询商品
     * @param categoryId
     * @return
     */
    List<Product> selectByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 根据分类id和状态查询商品
     * @param categoryId
     * @param status
     * @return
     */
    List<Product> selectByCategoryIdAndStatus(@Param("categoryId") Long categoryId, @Param("status") Integer status);

    /**
     * 修改商品
     * @param product
     */
    @AutoFill( OperationType.UPDATE)
    void updateProduct(Product product);


    /**
     * 根据id查询商品信息
     * @param ids
     * @return
     */
    List<Product> selectByIds(List<Long> ids);

    /**
     * 根据手机型号ID查询在售商品
     * @param phoneModelId
     * @return
     */
    List<Product> selectByPhoneModelId(Long phoneModelId);

    @Select("select id, name, stock, alert_threshold from product where id = #{id}")
    Product selectProductById(Long id);

    List<ProductVO> searchByKeyword(@Param("keyword") String keyword);

}
