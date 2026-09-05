package com.sky.product.internal.persistence;

import com.sky.annotation.AutoFill;
import com.sky.aspect.AutoFillAspect;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutoFillAspectTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void fillsAuditFieldsForMapperMovedIntoModulePersistencePackage() {
        BaseContext.setCurrentId(42L);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new ProductPersistenceProbe());
        proxyFactory.addAspect(new AutoFillAspect());
        ProductPersistenceProbe proxy = proxyFactory.getProxy();
        Employee employee = new Employee();

        proxy.insert(employee);

        assertNotNull(employee.getCreateTime());
        assertNotNull(employee.getUpdateTime());
        assertEquals(42L, employee.getCreateUser());
        assertEquals(42L, employee.getUpdateUser());
    }

    static class ProductPersistenceProbe {
        @AutoFill(OperationType.INSERT)
        public void insert(Employee employee) {
        }
    }
}
