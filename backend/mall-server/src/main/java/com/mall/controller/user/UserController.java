package com.mall.controller.user;

import com.mall.constant.JwtClaimsConstant;
import com.mall.dto.UserLoginDTO;
import com.mall.entity.User;
import com.mall.properties.JwtProperties;
import com.mall.result.Result;
import com.mall.service.TokenSessionService;
import com.mall.service.UserService;
import com.mall.utils.JwtUtil;
import com.mall.vo.UserLoginVO;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;
    
    @Autowired
    private TokenSessionService tokenSessionService;

    /**
     * 登录
     * @param  userLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation(value = "用户登录")
    public Result<UserLoginVO> login(@RequestBody @Valid UserLoginDTO userLoginDTO){
        String code = userLoginDTO == null ? null : userLoginDTO.getCode();
        log.info("用户登录，codeLength={}", code == null ? null : code.length());
        User user = userService.wxlogin(userLoginDTO);
            
        // 创建会话并写入 Redis（使用 JWT 配置的 TTL）
        String sessionId = tokenSessionService.createUserSession(user.getId(), user.getName(), jwtProperties.getUserTtl());
            
        //构建 jwt 令牌
        Map<String,Object> claim = new HashMap<>();
        claim.put(JwtClaimsConstant.USER_ID,user.getId());
        claim.put(JwtClaimsConstant.SESSION_ID, sessionId);
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claim);
        //封装 VO
        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build();
                return Result.success(userLoginVO);
    
    }

    /**
     * 用户登出
     * @param request HttpServletRequest
     * @return
     */
    @PostMapping("/logout")
    @ApiOperation(value = "用户登出")
    public Result<String> logout(HttpServletRequest request) {
        // 从请求头获取 token
        String token = request.getHeader(jwtProperties.getUserTokenName());
        
        if (token != null && !token.isEmpty()) {
            try {
                // 解析 token 获取 sessionId
                Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
                String sessionId = claims.get(JwtClaimsConstant.SESSION_ID).toString();
                
                // 删除 Redis 中的会话
                tokenSessionService.removeUserSession(sessionId);
                log.info("用户登出成功，sessionId={}", sessionId);
            } catch (Exception e) {
                log.warn("登出时解析 token 失败：{}", e.getMessage());
                // 即使 token 无效也返回成功，避免暴露信息
            }
        }
        
        return Result.success();
    }

}
