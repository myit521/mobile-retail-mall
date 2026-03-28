package com.mall.service.impl;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.constant.CacheConstant;
import com.mall.constant.MessageConstant;
import com.mall.dto.ProductDTO;
import com.mall.dto.ProductPageQueryDTO;
import com.mall.entity.PhoneModel;
import com.mall.entity.Product;
import com.mall.entity.ProductPhoneModel;
import com.mall.entity.ProductSpec;
import com.mall.exception.BaseException;
import com.mall.mapper.PhoneModelMapper;
import com.mall.mapper.ProductPhoneModelMapper;
import com.mall.mapper.ProductSpecMapper;
import com.mall.mapper.ProductMapper;
import com.mall.result.PageResult;
import com.mall.service.ProductService;
import com.mall.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSpecMapper productSpecMapper;
    @Autowired
    private ProductPhoneModelMapper productPhoneModelMapper;
    @Autowired
    private PhoneModelMapper phoneModelMapper;

    @Transactional
    @Override
    @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    public Long save(ProductDTO productDTO) {
        log.info("新增商品，参数：{}",productDTO);
        Product product = new Product();
        //拷贝
        BeanUtils.copyProperties(productDTO,product);
        productMapper.insertProduct(product);

        //新增商品规格
        List<ProductSpec> specList = productDTO.getSpecList();
        if(specList != null && !specList.isEmpty()){
            for (ProductSpec spec : specList) {
                spec.setProductId(product.getId());
            }
            productSpecMapper.insertProductSpec(specList);
        }

        //新增商品与手机型号关联
        List<Long> phoneModelIds = productDTO.getPhoneModelIds();
        if(phoneModelIds != null && !phoneModelIds.isEmpty()){
            List<ProductPhoneModel> productPhoneModels = new ArrayList<>();
            for (Long phoneModelId : phoneModelIds) {
                ProductPhoneModel ppm = ProductPhoneModel.builder()
                        .productId(product.getId())
                        .phoneModelId(phoneModelId)
                        .build();
                productPhoneModels.add(ppm);
            }
            productPhoneModelMapper.insertBatch(productPhoneModels);
        }
        return product.getId();
    }

    @Override
    public PageResult page(ProductPageQueryDTO productPageQueryDTO) {
        //设置默认值，防止前端传递空值导致类型转换失败
        Integer pageNum = productPageQueryDTO.getPage() == null ? 1 : productPageQueryDTO.getPage();
        Integer pageSize = productPageQueryDTO.getPageSize() == null ? 10 : productPageQueryDTO.getPageSize();
        
        //开启分页
        PageHelper.startPage(pageNum, pageSize);
        Page<ProductVO> page =(Page<ProductVO>) productMapper.selectProductPage(productPageQueryDTO);
        log.info("商品分页查询结果：{}",page);
        //封装分页结果
        return new PageResult(page.getTotal(),page.getResult());
    }

    @Transactional
    @Override
    @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    public void delete(List<Long> ids) {

        //查询当前商品是否在售中，如果处于售中，则不能删除
            List<Product> products = productMapper.selectByIds(ids);
            for(Product product : products){
                if(product.getStatus() == 1){
                    //当前商品在售中，不能删除
                    throw new BaseException(MessageConstant.DISH_ON_SALE);
                }
            }

            // TODO: 电子产品模式：已删除套餐关联校验，直接删除商品和规格
        productMapper.deleteByIds(ids);
        productSpecMapper.deleteByProductIds(ids);
        //删除商品与手机型号关联
        productPhoneModelMapper.deleteByProductIds(ids);
    }

    @Override
    public List<Product> list(Long categoryId) {
        return productMapper.selectByCategoryId(categoryId);
    }

    @Override
    public ProductVO getById(Long id) {

        ProductVO productVO = productMapper.selectById(id);
        productVO.setSpecList(productSpecMapper.selectByProductId(id));
        //查询商品关联的手机型号
        List<PhoneModel> phoneModelList = productPhoneModelMapper.selectPhoneModelsByProductId(id);
        productVO.setPhoneModelList(phoneModelList);
        return productVO;
    }

    @Override
    @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    public void startOrStop(Integer status, Long id) {
        Product product = Product.builder()
                .id(id)
                .status(status)
                .build();
                productMapper.updateProduct(product);

    }

    @Transactional
    @Override
    @CacheEvict(value = CacheConstant.PRODUCT_CACHE, allEntries = true)
    public void update(ProductDTO productDTO) {
        log.info("修改商品：{}", productDTO);
        
        // 1. 检查商品是否存在
        Product existingProduct = productMapper.selectProductById(productDTO.getId());
        if (existingProduct == null) {
            throw new BaseException("商品不存在，无法更新");
        }
        
        // 2. 更新商品基本信息
        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);
        productMapper.updateProduct(product);
        
        // 3. 更新商品规格
        productSpecMapper.deleteByProductIds(Collections.singletonList(productDTO.getId()));
        if (productDTO.getSpecList() != null && !productDTO.getSpecList().isEmpty()) {
            for (ProductSpec spec : productDTO.getSpecList()) {
                spec.setProductId(productDTO.getId());
            }
            productSpecMapper.insertProductSpec(productDTO.getSpecList());
        }
        
        // 4. 更新商品与手机型号关联
        productPhoneModelMapper.deleteByProductId(productDTO.getId());
        List<Long> phoneModelIds = productDTO.getPhoneModelIds();
        if (phoneModelIds != null && !phoneModelIds.isEmpty()) {
            List<ProductPhoneModel> productPhoneModels = new ArrayList<>();
            for (Long phoneModelId : phoneModelIds) {
                ProductPhoneModel ppm = ProductPhoneModel.builder()
                        .productId(productDTO.getId())
                        .phoneModelId(phoneModelId)
                        .build();
                productPhoneModels.add(ppm);
            }
            productPhoneModelMapper.insertBatch(productPhoneModels);
        }
    }

    /**
     * 条件查询商品和规格（按分类）
     * @param product
     * @return
     */
    @Override
    @Cacheable(value = CacheConstant.PRODUCT_CACHE, key = "'category:' + #product.categoryId", unless = "#result == null || #result.isEmpty()")
    public List<ProductVO> listWithFlavor(Product product) {
        List<Product> productList;
        if (product.getStatus() == null) {
            productList = productMapper.selectByCategoryId(product.getCategoryId());
        } else {
            productList = productMapper.selectByCategoryIdAndStatus(product.getCategoryId(), product.getStatus());
        }

        if (productList == null || productList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> productIds = productList.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, List<ProductSpec>> specMap = buildSpecMap(productIds);
        Map<Long, List<PhoneModel>> phoneModelMap = buildPhoneModelMap(productIds);

        List<ProductVO> productVOList = new ArrayList<>();
        for (Product p : productList) {
            ProductVO productVO = new ProductVO();
            BeanUtils.copyProperties(p, productVO);
            productVO.setSpecList(specMap.getOrDefault(p.getId(), new ArrayList<>()));
            productVO.setPhoneModelList(phoneModelMap.getOrDefault(p.getId(), new ArrayList<>()));
            productVOList.add(productVO);
        }

        return productVOList;
    }

    /**
     * 根据手机型号ID查询商品
     * @param phoneModelId
     * @return
     */
    @Override
    @Cacheable(value = CacheConstant.PRODUCT_CACHE, key = "'phoneModel:' + #phoneModelId", unless = "#result == null || #result.isEmpty()")
    public List<ProductVO> listByPhoneModelId(Long phoneModelId) {
        List<Product> productList = productMapper.selectByPhoneModelId(phoneModelId);
        return enrichProductVOList(productList);
    }

    @Override
    public List<ProductVO> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductVO> searchedProducts = productMapper.searchByKeyword(keyword.trim());
        if (searchedProducts == null || searchedProducts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = searchedProducts.stream().map(ProductVO::getId).collect(Collectors.toList());
        Map<Long, List<ProductSpec>> specMap = buildSpecMap(productIds);
        Map<Long, List<PhoneModel>> phoneModelMap = buildPhoneModelMap(productIds);

        for (ProductVO productVO : searchedProducts) {
            productVO.setSpecList(specMap.getOrDefault(productVO.getId(), new ArrayList<>()));
            productVO.setPhoneModelList(phoneModelMap.getOrDefault(productVO.getId(), new ArrayList<>()));
        }

        return searchedProducts;
    }

    private List<ProductVO> enrichProductVOList(List<Product> productList) {
        if (productList == null || productList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> productIds = productList.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, List<ProductSpec>> specMap = buildSpecMap(productIds);
        Map<Long, List<PhoneModel>> phoneModelMap = buildPhoneModelMap(productIds);

        List<ProductVO> productVOList = new ArrayList<>();
        for (Product p : productList) {
            ProductVO productVO = new ProductVO();
            BeanUtils.copyProperties(p, productVO);
            productVO.setSpecList(specMap.getOrDefault(p.getId(), new ArrayList<>()));
            productVO.setPhoneModelList(phoneModelMap.getOrDefault(p.getId(), new ArrayList<>()));
            productVOList.add(productVO);
        }

        return productVOList;
    }

    private Map<Long, List<ProductSpec>> buildSpecMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductSpec> specList = productSpecMapper.selectByProductIds(productIds);
        if (specList == null || specList.isEmpty()) {
            return new HashMap<>();
        }
        return specList.stream().collect(Collectors.groupingBy(ProductSpec::getProductId));
    }

    private Map<Long, List<PhoneModel>> buildPhoneModelMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductPhoneModel> relations = productPhoneModelMapper.selectByProductIds(productIds);
        if (relations == null || relations.isEmpty()) {
            return new HashMap<>();
        }

        List<Long> phoneModelIds = relations.stream()
                .map(ProductPhoneModel::getPhoneModelId)
                .distinct()
                .collect(Collectors.toList());
        if (phoneModelIds.isEmpty()) {
            return new HashMap<>();
        }

        List<PhoneModel> phoneModels = phoneModelMapper.selectByIds(phoneModelIds);
        if (phoneModels == null || phoneModels.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, PhoneModel> phoneModelById = phoneModels.stream()
                .collect(Collectors.toMap(PhoneModel::getId, p -> p));

        Map<Long, List<PhoneModel>> phoneModelMap = new HashMap<>();
        for (ProductPhoneModel relation : relations) {
            PhoneModel model = phoneModelById.get(relation.getPhoneModelId());
            if (model == null) {
                continue;
            }
            phoneModelMap.computeIfAbsent(relation.getProductId(), key -> new ArrayList<>()).add(model);
        }
        return phoneModelMap;
    }

}
