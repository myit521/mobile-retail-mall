package com.sky.inventory.internal.application;

import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockAlertMapper;
import com.sky.inventory.internal.persistence.StockCheckPlanMapper;
import com.sky.inventory.internal.persistence.StockCheckRecordMapper;
import com.sky.inventory.internal.persistence.StockLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class StockServiceImplContextTest {

    @Test
    void startsWithOnlyItsFiveMapperCollaborators() {
        assertDoesNotThrow(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(StockServiceImpl.class, MapperCollaborators.class);
                context.refresh();
                context.getBean(StockServiceImpl.class);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MapperCollaborators {

        @Bean
        InventoryProductMapper inventoryProductMapper() {
            return mock(InventoryProductMapper.class);
        }

        @Bean
        StockLogMapper stockLogMapper() {
            return mock(StockLogMapper.class);
        }

        @Bean
        StockAlertMapper stockAlertMapper() {
            return mock(StockAlertMapper.class);
        }

        @Bean
        StockCheckPlanMapper stockCheckPlanMapper() {
            return mock(StockCheckPlanMapper.class);
        }

        @Bean
        StockCheckRecordMapper stockCheckRecordMapper() {
            return mock(StockCheckRecordMapper.class);
        }
    }
}
