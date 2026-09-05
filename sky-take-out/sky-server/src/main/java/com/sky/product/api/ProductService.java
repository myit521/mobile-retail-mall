package com.sky.product.api;

import com.sky.dto.ProductDTO;
import com.sky.dto.ProductPageQueryDTO;
import com.sky.entity.Product;
import com.sky.result.PageResult;
import com.sky.vo.ProductVO;

import java.util.List;

public interface ProductService {
    Long save(ProductDTO productDTO);

    PageResult page(ProductPageQueryDTO productPageQueryDTO);

    void delete(List<Long> ids);



    List<Product> list(Long categoryId);




    ProductVO getById(Long id);

    void startOrStop(Integer status, Long id);

    void update(ProductDTO productDTO);
    /**
     * 条件查询商品和规格
     * @param product
     * @return
     */
    List<ProductVO> listWithFlavor(Product product);

    /**
     * 根据手机型号ID查询商品列表
     * @param phoneModelId
     * @return
     */
    List<ProductVO> listByPhoneModelId(Long phoneModelId);

    List<ProductVO> search(String keyword);


}
