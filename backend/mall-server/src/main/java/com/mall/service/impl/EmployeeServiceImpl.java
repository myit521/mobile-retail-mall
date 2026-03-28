package com.mall.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.constant.MessageConstant;
import com.mall.constant.PasswordConstant;
import com.mall.constant.StatusConstant;
import com.mall.context.BaseContext;
import com.mall.dto.EmployeeDTO;
import com.mall.dto.EmployeeLoginDTO;
import com.mall.dto.EmployeePageQueryDTO;
import com.mall.dto.PasswordEditDTO;
import com.mall.entity.Employee;
import com.mall.exception.AccountLockedException;
import com.mall.exception.AccountNotFoundException;
import com.mall.exception.PasswordEditFailedException;
import com.mall.exception.PasswordErrorException;
import com.mall.exception.TooManyAttemptsException;
import com.mall.mapper.EmployeeMapper;
import com.mall.result.PageResult;
import com.mall.service.EmployeeService;
import com.mall.service.LoginAttemptService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        // 0、检查账号是否已被锁定（先检查，避免后续无效查询）
        loginAttemptService.checkLocked(username);

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在 - 同样记录失败次数，防止账号枚举攻击
            loginAttemptService.recordFailure(username);
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对，使用BCryptPasswordEncoder验证密码
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            //密码错误 - 记录失败次数
            loginAttemptService.recordFailure(username);
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、登录成功，清除失败记录
        loginAttemptService.clearFailure(username);

        //4、角色标准化处理（防御性处理历史脏数据）
        employee.setRole(normalizeRole(employee.getRole()));

        //5、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     * @param employeeDTO
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        //拷贝
        BeanUtils.copyProperties(employeeDTO, employee);
        //设置账号状态
        employee.setStatus(StatusConstant.ENABLE);
        //角色标准化处理
        String role = normalizeRole(employee.getRole());
        employee.setRole(role);
       // 设置默认密码
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
        employeeMapper.insertEmployee(employee);


    }

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult page(EmployeePageQueryDTO employeePageQueryDTO) {
        //设置默认值，防止前端传递空值导致类型转换失败
        Integer pageNum = employeePageQueryDTO.getPage() == null ? 1 : employeePageQueryDTO.getPage();
        Integer pageSize = employeePageQueryDTO.getPageSize() == null ? 10 : employeePageQueryDTO.getPageSize();
        
        //开启分页
        PageHelper.startPage(pageNum, pageSize);
        Page<Employee> page = ( Page<Employee>)employeeMapper.selectEmployeePage(employeePageQueryDTO);


        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    @Override
    public boolean startOrStop(Integer status, Long id) {
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();
                return employeeMapper.updateEmployee(employee) == 1;
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     * @return
     */
    @Override
    public boolean update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        //角色标准化处理
        String role = normalizeRole(employee.getRole());
        employee.setRole(role);

        return employeeMapper.updateEmployee(employee) == 1;
    }

    /**
     * 角色标准化处理
     * @param role 原始角色值
     * @return 标准化的角色（ADMIN 或 EMPLOYEE）
     */
    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "EMPLOYEE";
        }
        // 转大写并去除空格
        String upperRole = role.trim().toUpperCase();
        // 只允许 ADMIN 和 EMPLOYEE，其他都降级为 EMPLOYEE
        if ("ADMIN".equals(upperRole)) {
            return "ADMIN";
        }
        return "EMPLOYEE";
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id) {
        Employee employeeById = employeeMapper.getEmployeeById(id);
        employeeById.setPassword("****");
        return employeeById;
    }


    /**
     * 修改员工密码
     * @param passwordEditDTO
     * @return
     */
    @Override
    public boolean editPassword(PasswordEditDTO passwordEditDTO) {
        //参数校验
        if(passwordEditDTO == null || passwordEditDTO.getNewPassword() == null || passwordEditDTO.getNewPassword().equals("")) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }
        //判断旧密码是否正确
        Long currentEmployeeId = BaseContext.getCurrentId();
        if (currentEmployeeId == null) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }
        if(!passwordEncoder.matches(passwordEditDTO.getOldPassword(),employeeMapper.SelectEmployeePasswordById(currentEmployeeId))) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }
        Employee employee = Employee.builder()
                .id(currentEmployeeId)
                .password(passwordEncoder.encode(passwordEditDTO.getNewPassword()))//密码加密
                .build();
                return employeeMapper.updateEmployeePassword(employee) == 1;
    }

}
