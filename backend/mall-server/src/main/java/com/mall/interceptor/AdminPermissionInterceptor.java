package com.mall.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.mall.annotation.AdminPermission;
import com.mall.context.BaseContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * 管理员权限拦截器
 */
@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //只处理 HandlerMethod
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        //读取方法上的注解
        AdminPermission methodAnnotation = method.getAnnotation(AdminPermission.class);
        //如果方法上没有注解，读取类上的注解
        AdminPermission classAnnotation = handlerMethod.getBeanType().getAnnotation(AdminPermission.class);

        AdminPermission adminPermission = null;
        if (methodAnnotation != null) {
            adminPermission = methodAnnotation;
        } else if (classAnnotation != null) {
            adminPermission = classAnnotation;
        }

        //如果没有注解，放行
        if (adminPermission == null) {
            return true;
        }

        //从 BaseContext 获取当前角色
        String currentRole = BaseContext.getCurrentRole();
        
        //检查角色是否在允许列表中
        String[] allowedRoles = adminPermission.value();
        for (String allowedRole : allowedRoles) {
            if (allowedRole.equals(currentRole)) {
                return true;
            }
        }

        //权限不足，返回 403
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        JSONObject result = new JSONObject();
        result.put("code", 0);
        result.put("msg", "权限不足，无法执行该操作");
        response.getWriter().write(result.toJSONString());
        return false;
    }
}
