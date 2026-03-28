package com.mall.utils;

import com.mall.constant.AdminRoleConstant;
import com.mall.entity.Employee;

public class AdminRoleUtil {

    private AdminRoleUtil() {
    }

    public static String normalizeRole(String role) {
        if (AdminRoleConstant.ADMIN.equalsIgnoreCase(role)) {
            return AdminRoleConstant.ADMIN;
        }
        return AdminRoleConstant.EMPLOYEE;
    }

    public static String resolveEmployeeRole(Employee employee) {
        if (employee == null) {
            return AdminRoleConstant.EMPLOYEE;
        }
        if (employee.getRole() != null && !employee.getRole().trim().isEmpty()) {
            return normalizeRole(employee.getRole());
        }
        if ("admin".equalsIgnoreCase(employee.getUsername())) {
            return AdminRoleConstant.ADMIN;
        }
        return AdminRoleConstant.EMPLOYEE;
    }
}
