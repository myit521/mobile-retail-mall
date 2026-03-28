package com.mall.annotation;

import com.mall.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标识需要进行公共字段自动填充的方法
 */
@Target(ElementType.METHOD) // 该注解用于方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时生效
public @interface AutoFill {
    /**
     * 数据库操作类型：INSERT 或 UPDATE
     */
    OperationType value();
}
