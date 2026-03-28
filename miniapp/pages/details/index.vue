<template>
  <view>
    <!-- 导航 -->
    <uni-nav-bar @clickLeft="goBack" left-icon="back" leftIcon="arrowleft" title="订单详情" statusBar="true" fixed="true"
      color="#ffffff" backgroundColor="#333333"></uni-nav-bar>
    <!-- end -->
    <view class="order_content orderDetail">
      <view class="order_content_box" scroll-y="true" scroll-top="0rpx">
        <!-- 订单状态 -->
        <status ref="status" :timeout="timeout" :orderDetailsData="orderDetailsData" :rocallTime="rocallTime"
          @statusWord="statusWord" @handlePay="handlePay" @handleCancel="handleCancel"
          @handleRefund="handleRefund" @oneMoreOrder="oneMoreOrder"></status>
        <!-- end -->
        <!-- 订单详情 -->
        <order-detail :orderDataes="orderDataes" :orderDetailsData="orderDetailsData"
          :showDisplay="showDisplay"></order-detail>
        <!-- end -->
        <!-- 联系店铺 -->
        <view class="box contactMerchant">
          <button @click="handlePhone('bottom', orderDetailsData.shopTelephone)">
            <view class="phoneIcon"></view>
            联系店铺
          </button>
        </view>
        <!-- end -->
        <!-- 门店信息 -->
        <store-info :orderDetailsData="orderDetailsData"></store-info>
        <!-- end -->
        <!-- 订单信息 -->
        <order-info :orderDetailsData="orderDetailsData"></order-info>
        <!-- end -->
      </view>
      <!-- 联系店铺弹层 -->
      <uni-popup ref="commonPopup" class="comPopupBox">
        <view class="popup-content">
          <view class="text">{{ textTip }}</view>
          <view class="btn" v-if="showConfirm">
            <view @click="closePopupInfo">确认</view>
          </view>
          <view class="btn" v-else>
            <view @click="closePopupInfo">先等等</view>
            <view @click="handlePhone('bottom')">拨打电话</view>
          </view>
        </view>
      </uni-popup>
      <!-- 拨打电话弹层 -->
      <view class="container phoneCon">
        <uni-popup ref="phone" @change="change" class="popupBox">
          <view class="popup-content">
            <view>{{ phone }}</view>
            <view @click="call">呼叫</view>
            <view @click="closePopup" class="closePopup">取消</view>
          </view>
        </uni-popup>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script src="./index.js"></script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="../order/style.scss" lang="scss"></style>
