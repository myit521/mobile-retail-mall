package com.sky.config;

import com.sky.auth.api.EmployeeService;
import com.sky.auth.api.TokenSessionService;
import com.sky.auth.internal.controller.admin.EmployeeController;
import com.sky.constant.JwtClaimsConstant;
import com.sky.entity.Employee;
import com.sky.interceptor.AdminPermissionInterceptor;
import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.order.api.OrderApplicationService;
import com.sky.properties.AliOssProperties;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import com.sky.vo.OrderVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerContractTest {

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static EmployeeService employeeService;
    private static TokenSessionService tokenSessionService;
    private static OrderApplicationService orderService;
    private static JwtProperties jwtProperties;

    @BeforeAll
    static void setUpMvc() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ContractConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        employeeService = context.getBean(EmployeeService.class);
        tokenSessionService = context.getBean(TokenSessionService.class);
        orderService = context.getBean(OrderApplicationService.class);
        jwtProperties = context.getBean(JwtProperties.class);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void adminLoginKeepsItsAuthExclusionRequestBindingAndSuccessEnvelope() throws Exception {
        Employee employee = Employee.builder()
                .id(41L)
                .username("contract-admin")
                .name("Contract Admin")
                .role("ADMIN")
                .build();
        when(employeeService.login(any())).thenReturn(employee);
        when(tokenSessionService.createAdminSession(any(), anyLong())).thenReturn("admin-session");

        mvc.perform(post("/admin/employee/login")
                        .contentType("application/json")
                        .content("{\"username\":\"contract-admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(41))
                .andExpect(jsonPath("$.data.userName").value("contract-admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void migratedUserOrderRouteStillRequiresTheConfiguredAuthHeader() throws Exception {
        mvc.perform(get("/user/order/orderDetail/17"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void migratedUserOrderRouteBindsPathAndReturnsTheBaselineSuccessEnvelope() throws Exception {
        OrderVO order = new OrderVO();
        order.setId(17L);
        order.setNumber("ORD-CONTRACT-17");
        when(orderService.show(17L)).thenReturn(order);
        when(tokenSessionService.isUserSessionValid("user-session")).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 29L);
        claims.put(JwtClaimsConstant.SESSION_ID, "user-session");
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        mvc.perform(get("/user/order/orderDetail/17")
                        .header(jwtProperties.getUserTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(17))
                .andExpect(jsonPath("$.data.number").value("ORD-CONTRACT-17"));
    }

    @Configuration
    @Import(WebMvcConfiguration.class)
    static class ContractConfiguration {

        @Bean
        EmployeeController employeeController() {
            return new EmployeeController();
        }

        @Bean
        com.sky.order.internal.controller.user.OrderController userOrderController() {
            return new com.sky.order.internal.controller.user.OrderController();
        }

        @Bean
        EmployeeService employeeService() {
            return mock(EmployeeService.class);
        }

        @Bean
        OrderApplicationService orderApplicationService() {
            return mock(OrderApplicationService.class);
        }

        @Bean
        TokenSessionService tokenSessionService() {
            return mock(TokenSessionService.class);
        }

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setAdminSecretKey("task-five-admin-contract-secret");
            properties.setAdminTtl(60_000L);
            properties.setAdminTokenName("token");
            properties.setUserSecretKey("task-five-user-contract-secret");
            properties.setUserTtl(60_000L);
            properties.setUserTokenName("authentication");
            return properties;
        }

        @Bean
        AliOssProperties aliOssProperties() {
            return new AliOssProperties();
        }

        @Bean
        JwtTokenAdminInterceptor jwtTokenAdminInterceptor() {
            return new JwtTokenAdminInterceptor();
        }

        @Bean
        JwtTokenUserInterceptor jwtTokenUserInterceptor() {
            return new JwtTokenUserInterceptor();
        }

        @Bean
        AdminPermissionInterceptor adminPermissionInterceptor() {
            return new AdminPermissionInterceptor();
        }
    }
}
