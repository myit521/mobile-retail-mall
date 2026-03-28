package com.mall.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.mall.constant.MessageConstant;
import com.mall.dto.UserLoginDTO;
import com.mall.entity.User;
import com.mall.exception.LoginFailedException;
import com.mall.mapper.UserMapper;
import com.mall.properties.WeChatProperties;
import com.mall.service.UserService;
import com.mall.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    //微信服务接口地址
    private static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

    @Override
    public User wxlogin(UserLoginDTO userLoginDTO) {
        //调用微信接口获取openid
        String openid = getOpenidFromWechat(userLoginDTO.getCode());

        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        //查询是否已经存在
        User user = userMapper.selectByOpenid(openid);

        if (user == null) {
            //不存在则插入
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }

        return user;

    }
    
    /**
     * 调用微信接口服务获取openid
     * @param code 微信授权码
     * @return openid
     */
    private String getOpenidFromWechat(String code) {
        //构建参数
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put("appid", weChatProperties.getAppid());
        paramMap.put("secret", weChatProperties.getSecret());
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");
        //发送请求
        String date = HttpClientUtil.doGet(WX_LOGIN_URL, paramMap);
        //解析结果
        JSONObject jsonObject = JSONObject.parseObject(date);//转换成json对象
        return jsonObject.getString("openid");
    }
}