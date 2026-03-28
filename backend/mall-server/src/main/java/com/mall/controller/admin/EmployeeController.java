package com.mall.controller.admin;

import com.mall.constant.JwtClaimsConstant;
import com.mall.constant.MessageConstant;
import com.mall.dto.EmployeeDTO;
import com.mall.dto.EmployeeLoginDTO;
import com.mall.dto.EmployeePageQueryDTO;
import com.mall.dto.PasswordEditDTO;
import com.mall.entity.Employee;
import com.mall.annotation.AdminPermission;
import com.mall.properties.JwtProperties;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.EmployeeService;
import com.mall.service.TokenSessionService;
import com.mall.utils.JwtUtil;
import com.mall.vo.EmployeeLoginVO;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")
@AdminPermission({"ADMIN"})
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private TokenSessionService tokenSessionService;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @ApiOperation("员工登录")
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@RequestBody @Valid EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO == null ? null : employeeLoginDTO.getUsername();
        log.info("员工登录，username={}", username);

        Employee employee = employeeService.login(employeeLoginDTO);

        // 创建会话并写入 Redis（使用 JWT 配置的 TTL）
        String sessionId = tokenSessionService.createAdminSession(employee, jwtProperties.getAdminTtl());

        //登录成功后，生成 jwt 令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        claims.put(JwtClaimsConstant.EMP_ROLE, employee.getRole());
        claims.put(JwtClaimsConstant.SESSION_ID, sessionId);
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);
        
        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .role(employee.getRole())
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @ApiOperation("员工退出")
    @PostMapping("/logout")
    @AdminPermission({"ADMIN", "EMPLOYEE"})
    public Result<String> logout(HttpServletRequest request) {
        // 从请求头获取 token
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        
        if (token != null && !token.isEmpty()) {
            try {
                // 解析 token 获取 sessionId
                Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
                String sessionId = claims.get(JwtClaimsConstant.SESSION_ID).toString();
                
                // 删除 Redis 中的会话
                tokenSessionService.removeAdminSession(sessionId);
                log.info("管理员登出成功，sessionId={}", sessionId);
            } catch (Exception e) {
                log.warn("登出时解析 token 失败：{}", e.getMessage());
                // 即使 token 无效也返回成功，避免暴露信息
            }
        }
        
        return Result.success();
    }

    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    @ApiOperation("新增员工")
    @PostMapping
    public Result save(@RequestBody EmployeeDTO employeeDTO){
        log.info("新增员工，username={}, name={}", employeeDTO == null ? null : employeeDTO.getUsername(), employeeDTO == null ? null : employeeDTO.getName());
        if(employeeDTO != null) {
            employeeService.save(employeeDTO);
            return Result.success();
        }
        return Result.error(MessageConstant.EMPLOYEE_NOT_FOUND);
    }
    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @ApiOperation("员工分页查询")
    @GetMapping("/page")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询：{}",employeePageQueryDTO);
        PageResult pageResult = employeeService.page(employeePageQueryDTO);
        return Result.success(pageResult);
    }
    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     * @return
     */
    @ApiOperation("启用禁用员工账号")
    @PostMapping("/status/{status}")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id){
        log.info("启用禁用员工账号：{}",id);
        if (status == null || id == null|| id <= 0)
            return Result.error(MessageConstant.INVALID_PARAM);

        if(!employeeService.startOrStop(status,id))
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        return Result.success();
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @ApiOperation("根据id查询员工信息")
    @GetMapping("/{id}")
    public Result<Employee> getById(@PathVariable Long id){
        log.info("根据id查询员工信息：{}",id);
        return Result.success(employeeService.getById(id));
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    @ApiOperation("编辑员工信息")
    @PutMapping
    public Result update(@RequestBody EmployeeDTO employeeDTO){
        log.info("编辑员工信息，id={}, username={}, name={}", employeeDTO == null ? null : employeeDTO.getId(), employeeDTO == null ? null : employeeDTO.getUsername(), employeeDTO == null ? null : employeeDTO.getName());
        if(employeeDTO == null)
            return Result.error(MessageConstant.INVALID_PARAM);
        if(!employeeService.update(employeeDTO))
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        return Result.success();
    }
    /**
     * 更改员工密码
     * @param  passwordEditDTO
     */
    @ApiOperation("更改员工密码")
    @PostMapping("/editPassword")
    @AdminPermission({"ADMIN", "EMPLOYEE"})
    public Result editPassword(@RequestBody @Valid PasswordEditDTO passwordEditDTO ){
        log.info("更改员工密码，empId={}", passwordEditDTO == null ? null : passwordEditDTO.getEmpId());
        if(passwordEditDTO == null)
            return Result.error(MessageConstant.INVALID_PARAM);
        if(!employeeService.editPassword(passwordEditDTO))
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        return Result.success();
    }

}
