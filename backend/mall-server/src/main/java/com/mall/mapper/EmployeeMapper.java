package com.mall.mapper;

import com.mall.annotation.AutoFill;

import com.mall.dto.EmployeePageQueryDTO;
import com.mall.entity.Employee;
import com.mall.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 插入员工数据
     * @param employee
     */
    @AutoFill(OperationType.INSERT)
    void insertEmployee(Employee employee);

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    List<Employee> selectEmployeePage(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 修改员工状态
     */
    @AutoFill(OperationType.UPDATE)
    int updateEmployee(Employee employee);

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @Select("select * from employee where id = #{id}")
    Employee getEmployeeById(Long id);
    /**
     * 查询员工密码
     */
    @Select("select password from employee where id = #{id}")
    String SelectEmployeePasswordById(Long id);

    /**
     * 修改员工密码
     * @param employee
     */
    @AutoFill(OperationType.UPDATE)
    @Update("update employee set password = #{password} where id = #{id}")
    int updateEmployeePassword(Employee employee);
}
