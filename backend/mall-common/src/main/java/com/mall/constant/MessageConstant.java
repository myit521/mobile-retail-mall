package com.mall.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {



    public static final String EMPLOYEE_NOT_FOUND = "员工信息为空";
    public static final String PLEASE_SELECT_DELETE_SETMEAL = "请选择需要删除的内容";
    public static final String INVALID_PARAM = "参数异常";
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String ALREADY_EXISTS = "已存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了其他内容,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了商品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的商品不能删除";
    public static final String SETMEAL_ON_SALE = "启用中的关联内容不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前商品关联了其他内容,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ORDER_CANCELLED = "订单已取消";
    public static final String ORDER_FAILED = "下单失败";
    public static final String ORDER_CONFIRM_SUCCESS = "确认订单成功";
    public static final String ORDER_CONFIRM_FAILED = "确认订单失败";
    public static final String ORDER_REJECTION_SUCCESS = "驳回订单成功";
    public static final String ORDER_REJECTION_FAILED = "驳回订单失败";
    public static final String ORDER_NOT_PAID = "订单未支付，无法确认";
    public static final String ORDER_PAYMENT_TIMEOUT = "订单支付超时";
    public static final String ORDER_DELIVERY_FAILED = "订单处理失败";
    public static final String ORDER_COMPLETION_FAILED = "订单完成失败";

    public static final String ORDER_CANCEL_FAILED = "取消订单失败" ;

    public static final String STOCK_NOT_ENOUGH = "商品库存不足";
    public static final String STOCK_PRODUCT_NOT_FOUND = "库存操作商品不存在";

    public static final String ACCOUNT_LOCKED_BY_RETRY = "密码错误次数过多，账号已被锁定%d分钟";
    public static final String LOGIN_TOO_MANY_ATTEMPTS = "登录失败次数过多，请%d分钟后再试";
    public static final String ORDER_USER_ERROR = "用户信息异常";
}
