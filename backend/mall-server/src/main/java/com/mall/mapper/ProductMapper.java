package com.mall.mapper;

import com.mall.annotation.AutoFill;
import com.mall.dto.ProductDTO;
import com.mall.dto.ProductPageQueryDTO;
import com.mall.entity.Product;
import com.mall.enumeration.OperationType;
import com.mall.vo.ProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 乐观锁扣减库存（WHERE stock >= quantity 保证不超卖）
     * @param productId
     * @param quantity
     * @return 影响行数，0表示库存不足
     */
    @Update("update product set stock = stock - #{quantity} where id = #{productId} and stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 归还库存
     * @param productId
     * @param quantity
     */
    @Update("update product set stock = stock + #{quantity} where id = #{productId}")
    void returnStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 查询低库存商品（stock <= alert_threshold）
     * @return
     */
    @Select("select id, name, stock, alert_threshold from product where status = 1 and stock <= alert_threshold")
    List<Product> selectLowStock();

    /**
     * 根据ID查询商品（仅基本信息，用于库存操作）
     * @param id
     * @return
     */
    @Select("select id, name, stock, alert_threshold from product where id = #{id}")
    Product selectProductById(Long id);

    List<ProductVO> searchByKeyword(@Param("keyword") String keyword);

}
