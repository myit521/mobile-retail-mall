package com.mall.mapper;

import com.mall.annotation.AutoFill;
import com.mall.entity.User;
import com.mall.enumeration.OperationType;
import io.swagger.annotations.ApiOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User selectByOpenid(String openid);

    /**
     * 插入数据
     * @param user
     */
    //@AutoFill( OperationType.INSERT)
    void insert(User user);

    /**
     * 根据id查询
     * @param userId
     * @return
     */
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);
}
