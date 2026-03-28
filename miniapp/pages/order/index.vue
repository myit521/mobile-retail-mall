<!--确认信息-->
<template>
  <view>
    <!-- 导航 -->
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="确认信息"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#333333"
    ></uni-nav-bar>
    <!-- end -->
    <view class="order_content">
      <view class="order_content_box">
        <!-- 订单明细 -->
        <view class="order_list_cont">
          <!-- 商品详情 -->
          <product-detail
            :orderDataes="orderDataes"
            :showDisplay="showDisplay"
            :orderDishNumber="orderDishNumber"
            :orderListDataes="orderListDataes"
            :orderDishPrice="orderDishPrice"
          ></product-detail>
          <!-- end -->
          <view class="boxPad">
            <!-- 备注信息 -->
            <order-remark :remark="remark" @goRemark="goRemark"></order-remark>
            <!-- end -->
          </view>
        </view>
        <!-- end -->
      </view>
      <!-- 底部汇总 -->
      <view class="footer_order_buttom order_form">
        <view class="order_number order_number_placeholder active">
          <view class="order_number_icon_text">单</view>
          <view class="order_dish_num"> {{ orderDishNumber }} </view>
        </view>
        <view class="order_price">
          <text class="ico">￥ </text> {{ Number(orderDishPrice || 0).toFixed(2) }}
        </view>
        <view class="order_but">
          <view class="order_but_rit disabled-btn">
            暂未开放支付功能
          </view>
        </view>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script src="./index.js"></script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./style.scss" lang="scss" scoped></style>
<style scoped>
.disabled-btn {
  opacity: 0.5;
}
</style>
