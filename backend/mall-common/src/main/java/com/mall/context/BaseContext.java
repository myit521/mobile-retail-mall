package com.mall.context;

/**
 * 基础上下文类，用于ThreadLocal存储当前登录用户ID
 */
public class BaseContext {

    /**
     * ThreadLocal存储当前登录用户的ID
     */
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /**
     * 设置当前登录用户的ID
     * @param id 用户ID
     */
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    /**
     * 获取当前登录用户的ID
     * @return 用户ID
     */
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    /**
     * 清理当前线程的 ID
     */
    public static void removeCurrentId() {
        threadLocal.remove();
    }

    /**
     * ThreadLocal 存储当前登录用户的角色
     */
    public static ThreadLocal<String> roleThreadLocal = new ThreadLocal<>();

    /**
     * 设置当前登录用户的角色
     * @param role 用户角色
     */
    public static void setCurrentRole(String role) {
        roleThreadLocal.set(role);
    }

    /**
     * 获取当前登录用户的角色
     * @return 用户角色
     */
    public static String getCurrentRole() {
        return roleThreadLocal.get();
    }

    /**
     * 清理当前线程的角色
     */
    public static void removeCurrentRole() {
        roleThreadLocal.remove();
    }

    /**
     * 清理所有 ThreadLocal
     */
    public static void clear() {
        removeCurrentId();
        removeCurrentRole();
    }

}
