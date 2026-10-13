package com.sky.product.internal.application;

import com.sky.dto.ProductDTO;
import com.sky.entity.Product;
import com.sky.product.internal.persistence.ProductMapper;
import com.sky.product.internal.persistence.ProductPhoneModelMapper;
import com.sky.product.internal.persistence.ProductSpecMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceStockTest {
    @Test
    void editDoesNotForwardStaleInventoryToPersistence() {
        ProductMapper products = mock(ProductMapper.class);
        when(products.selectProductById(7L)).thenReturn(Product.builder().id(7L).stock(8).build());
        ProductServiceImpl service = new ProductServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "productSpecMapper", mock(ProductSpecMapper.class));
        ReflectionTestUtils.setField(service, "productPhoneModelMapper", mock(ProductPhoneModelMapper.class));
        ProductDTO dto = new ProductDTO();
        dto.setId(7L);
        dto.setName("renamed-item");
        dto.setStock(10);

        service.update(dto);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products).updateProduct(saved.capture());
        assertThat(saved.getValue().getStock()).isNull();
        assertThat(saved.getValue().getName()).isEqualTo("renamed-item");
    }
}
