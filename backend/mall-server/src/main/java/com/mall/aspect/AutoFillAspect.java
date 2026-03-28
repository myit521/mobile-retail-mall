package com.mall.aspect;

import com.mall.annotation.AutoFill;
import com.mall.constant.AutoFillConstant;
import com.mall.context.BaseContext;
import com.mall.entity.Employee;
import com.mall.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MemberSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("execution(* com.mall.mapper.*.*(..)) && @annotation(com.mall.annotation.AutoFill)")
    public void autoFillPointCut(){}


    /**
     * 前置通知，在方法执行前进行执行
     */
@Before("autoFillPointCut()")
public void autoFill(JoinPoint joinPoint) {
    //获取当前方法参数
    Object[] args = joinPoint.getArgs();
    if (args == null || args.length == 0) {
        return;
    }
    Object arg = args[0];

    // 获取方法签名
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    // 获取方法注解
    AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
    if (autoFill == null) {
        return;
    }

    // 获取注解的值，获取填充方式
    OperationType value = autoFill.value();

    // 准备赋值的数据
    LocalDateTime now = LocalDateTime.now();
    Long currentId = BaseContext.getCurrentId();

    log.info("开始进行数据填充");

    try {
        // 判断参数是否为集合类型
        if (arg instanceof List) {
            // 如果是集合，遍历处理每个元素
            List<?> argList = (List<?>) arg;
            for (Object item : argList) {
                if (item != null) {
                    fillFieldValue(item, value, now, currentId);
                }
            }
        } else {
            // 单个对象直接处理
            fillFieldValue(arg, value, now, currentId);
        }
    } catch (Exception e) {
        log.error("自动填充字段发生错误：", e);
    }
}

/**
 * 填充字段值
 * @param obj 目标对象
 * @param operationType 操作类型
 * @param now 当前时间
 * @param currentId 当前用户 ID
 */
private void fillFieldValue(Object obj, OperationType operationType, LocalDateTime now, Long currentId) {
    Class<?> clazz = obj.getClass();
        
    if (operationType == OperationType.INSERT) {
        setFieldValue(obj, clazz, AutoFillConstant.SET_CREATE_TIME, now);
        setFieldValue(obj, clazz, AutoFillConstant.SET_UPDATE_TIME, now);
        setFieldValue(obj, clazz, AutoFillConstant.SET_CREATE_USER, currentId);
        setFieldValue(obj, clazz, AutoFillConstant.SET_UPDATE_USER, currentId);
    } else if (operationType == OperationType.UPDATE) {
        setFieldValue(obj, clazz, AutoFillConstant.SET_UPDATE_TIME, now);
        setFieldValue(obj, clazz, AutoFillConstant.SET_UPDATE_USER, currentId);
    }
}

/**
 * 使用反射设置对象字段值
 * @param obj 目标对象
 * @param clazz 目标类
 * @param setterName setter方法名
 * @param value 要设置的值
 */
private void setFieldValue(Object obj, Class<?> clazz, String setterName, Object value) {
    try {
        // 获取对应的方法
        Method setterMethod = clazz.getDeclaredMethod(setterName, value.getClass());
        setterMethod.invoke(obj, value);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        log.warn("未能找到或调用方法 [{}]，原因: {}",
                setterName,
                 e.getMessage());
    }
}



}
