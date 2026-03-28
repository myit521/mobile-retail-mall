package com.mall.service;

import com.mall.dto.EmployeeDTO;
import com.mall.dto.EmployeeLoginDTO;
import com.mall.dto.EmployeePageQueryDTO;
import com.mall.dto.PasswordEditDTO;
import com.mall.entity.Employee;
import com.mall.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    void save(EmployeeDTO employeeDTO);

    PageResult page(EmployeePageQueryDTO employeePageQueryDTO);

    boolean  startOrStop(Integer status, Long id);

    boolean update(EmployeeDTO employeeDTO);

    Employee getById(Long id);

    boolean editPassword(PasswordEditDTO passwordEditDTO);
}
