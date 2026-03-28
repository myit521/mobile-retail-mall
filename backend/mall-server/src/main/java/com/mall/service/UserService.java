package com.mall.service;

import com.mall.dto.UserLoginDTO;
import com.mall.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 微信登录
     * @param userLoginDTO 用户登录信息
     * @return 用户对象
     */
    User wxlogin(UserLoginDTO userLoginDTO);
}
