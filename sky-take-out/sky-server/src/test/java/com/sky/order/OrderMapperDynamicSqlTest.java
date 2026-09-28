package com.sky.order;

import com.sky.entity.Orders;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperDynamicSqlTest {

    @Test
    void insertBuildsSqlForCurrentRetailOrderModel() throws Exception {
        Configuration configuration = new Configuration();
        try (Reader mapper = Resources.getResourceAsReader("mapper/OrderMapper.xml")) {
            new XMLMapperBuilder(mapper, configuration, "mapper/OrderMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        Orders order = Orders.builder()
                .number("20260915000000000001")
                .status(Orders.PENDING_PAYMENT)
                .userId(1L)
                .orderTime(LocalDateTime.of(2026, 9, 15, 0, 0))
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("1.00"))
                .remark("PERF:test:1")
                .build();

        BoundSql boundSql = configuration
                .getMappedStatement("com.sky.order.internal.persistence.OrderMapper.insert")
                .getBoundSql(order);

        assertThat(boundSql.getSql()).contains("insert into orders", "user_id", "amount");
    }
}
