package com.sky.product.api;

import com.sky.entity.Product;
import java.util.List;

public interface ProductCatalog {
    List<Product> selectByIds(List<Long> ids);
}
