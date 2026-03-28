package com.mall.controller.user;

import com.mall.dto.OrdersPageQueryDTO;
import com.mall.dto.OrdersPaymentDTO;
import com.mall.dto.OrdersSubmitDTO;
import com.mall.mapper.OrderDetailMapper;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.OrderService;
import com.mall.vo.OrderPaymentVO;
import com.mall.vo.OrderSubmitVO;
import com.mall.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody @Valid OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        return Result.success(orderService.submit(ordersSubmitDTO));
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }
    /**
     * 历史订单列表
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单列表")
    public Result<PageResult> historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("历史订单列表：{}", ordersPageQueryDTO);
        return Result.success(orderService.pageQuery4User(ordersPageQueryDTO));
    }
    /**
     * 订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("订单详情")
    public Result<OrderVO> show(@PathVariable Long id) {
        log.info("订单详情：{}", id);
        return Result.success(orderService.show(id));
    }
    /**
     * 取消订单
     *
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id) {
        log.info("取消订单：{}", id);
        return orderService.userCancel(id);
    }
    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id) {
        log.info("再来一单：{}", id);
        return orderService.repetition(id);
    }



}
