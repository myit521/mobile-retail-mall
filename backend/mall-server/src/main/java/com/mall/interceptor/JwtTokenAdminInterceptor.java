package com.mall.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.mall.constant.JwtClaimsConstant;
import com.mall.constant.MessageConstant;
import com.mall.context.BaseContext;
import com.mall.properties.JwtProperties;
import com.mall.service.TokenSessionService;
import com.mall.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;
    
    @Autowired
    private TokenSessionService tokenSessionService;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //2、校验令牌
        try {
            log.info("admin jwt 校验，tokenLength={}", token == null ? null : token.length());
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            // 从 JWT 中获取角色（新 token 必须包含 empRole claim）
            String role = claims.get(JwtClaimsConstant.EMP_ROLE).toString();
            // 从 JWT 中获取 sessionId 并验证 Redis 会话
            String sessionId = claims.get(JwtClaimsConstant.SESSION_ID).toString();
            if (!tokenSessionService.isAdminSessionValid(sessionId)) {
                log.warn("admin session 无效，sessionId={}", sessionId);
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                JSONObject result = new JSONObject();
                result.put("code", 0);
                result.put("msg", "登录已过期，请重新登录");
                response.getWriter().write(result.toJSONString());
                return false;
            }
            BaseContext.setCurrentId(empId);//设置当前线程的当前登录用户的 ID
            BaseContext.setCurrentRole(role);
            log.info("当前员工 id：{}，角色：{}，sessionId={}", empId, role, sessionId);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            log.warn("admin jwt校验失败：{}", ex.getClass().getSimpleName());
            //4、不通过，响应401状态码
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            JSONObject result = new JSONObject();
            result.put("code", 0);
            result.put("msg", MessageConstant.USER_NOT_LOGIN);
            response.getWriter().write(result.toJSONString());
            return false;
        }
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //用完之后，清理 ThreadLocal 中的数据，避免内存泄漏
        BaseContext.clear();
    }
}
