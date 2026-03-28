package com.mall.controller.admin;

import com.mall.dto.OrdersCancelDTO;
import com.mall.dto.OrdersConfirmDTO;
import com.mall.dto.OrdersPageQueryDTO;
import com.mall.dto.OrdersRejectionDTO;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.OrderService;
import com.mall.vo.OrderStatisticsVO;
import com.mall.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController( "adminOrderController")
@RequestMapping("/admin/order")
@Api(tags = "订单接口")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @RequestMapping("/conditionSearch")
    @ApiOperation("订单搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("订单搜索：{}", ordersPageQueryDTO);
        return Result.success(orderService.conditionSearch(ordersPageQueryDTO));
    }
    /**
     * 各个状态的订单数量统计
     * @return
     */
    @RequestMapping("/statistics")
    @ApiOperation("各个状态的订单数量统计")
    public Result<OrderStatisticsVO> statistics(){
        log.info("各个状态的订单数量统计");
        return Result.success(orderService.statistics());
    }

    /**
     * 订单详情
     * @param id 订单id
     * @return
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id){
        log.info("查询订单详情：{}", id);
        OrderVO orderVO = orderService.show(id);
        return Result.success(orderVO);
    }
    /**
     * 接单
     * @param ordersConfirmDTO
     * @return
     */
    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result confirm(@RequestBody @Valid OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单：{}", ordersConfirmDTO);
        return Result.success(orderService.confirm(ordersConfirmDTO));
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    @PutMapping("/rejection")
    @ApiOperation("拒单")
    public Result rejection(@RequestBody @Valid OrdersRejectionDTO ordersRejectionDTO){
        log.info("拒单：{}", ordersRejectionDTO);
        return Result.success(orderService.rejection(ordersRejectionDTO));
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     * @return
     */
    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO){
        log.info("取消订单：{}", ordersCancelDTO);
        return Result.success(orderService.adminCancel(ordersCancelDTO));

    }

}
