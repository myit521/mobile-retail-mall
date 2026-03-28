package com.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {

    //员工id
    @NotNull(message = "员工ID不能为空")
    private Long empId;

    //旧密码
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    //新密码
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

}
