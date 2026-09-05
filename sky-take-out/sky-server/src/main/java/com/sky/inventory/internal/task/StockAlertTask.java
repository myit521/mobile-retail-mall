package com.sky.inventory.internal.task;

import com.sky.entity.Product;
import com.sky.entity.StockAlert;
import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockAlertMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class StockAlertTask {

    @Autowired
    private InventoryProductMapper productMapper;
    @Autowired
    private StockAlertMapper stockAlertMapper;

    /**
     * 每天上午9点扫描低库存商品并生成预警
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkLowStock() {
        log.info("开始扫描低库存商品...");
        List<Product> lowStockProducts = productMapper.selectLowStock();
        if (lowStockProducts == null || lowStockProducts.isEmpty()) {
            log.info("未发现低库存商品");
            return;
        }

        int count = 0;
        for (Product product : lowStockProducts) {
            // 检查是否已有未处理的预警，避免重复生成
            Integer pendingCount = stockAlertMapper.countPendingByProductId(product.getId());
            if (pendingCount != null && pendingCount > 0) {
                continue;
            }

            StockAlert alert = StockAlert.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .currentStock(product.getStock())
                    .alertThreshold(product.getAlertThreshold())
                    .alertStatus(StockAlert.STATUS_PENDING)
                    .alertTime(LocalDateTime.now())
                    .build();
            stockAlertMapper.insert(alert);
            count++;
        }
        log.info("低库存预警扫描完成，新增 {} 条预警记录", count);
    }
}
