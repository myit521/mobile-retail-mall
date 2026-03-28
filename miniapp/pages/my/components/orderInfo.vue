<!--我的头部-->
<template>
  <view class="box address_order">
    <!-- 历史订单 -->
    <view class="order first_order" @click="goOrder">
      <view class="entry_icon">订单</view>
      <text class="order_word">历史订单</text>
      <image class="to_right" src="../../../static/toRight2.png" mode=""></image>
    </view>
    <!-- 手机型号推荐 -->
    <view class="order phone_model" @click="goPhoneModel">
      <view class="entry_icon">机型</view>
      <text class="order_word">手机型号推荐</text>
      <image class="to_right" src="../../../static/toRight2.png" mode=""></image>
    </view>
  </view>
</template>
<script>
export default {
  methods: {
    //去历史订单页
    goOrder() {
      this.$emit("goOrder");
    },
    // 去手机型号推荐页
    goPhoneModel() {
      this.$emit("goPhoneModel");
    },
  },
};
</script>
<style lang="scss" scoped>
    .address_order {
      width: 710rpx;
      min-height: 200rpx;
      height: auto;
      margin: 20rpx auto;
      margin-top: 0;
      padding: 6rpx 0 10rpx;
      box-sizing: border-box;
      background: #ffffff;
      border-radius: 24rpx;
      box-shadow: 0 12rpx 28rpx rgba(31, 41, 55, 0.05);
      .order {
        line-height: 100rpx;
        position: relative;
        border-top: 1px solid #eef2f7;
        margin-left: 30rpx;
        margin-right: 20rpx;
        display: flex;
        align-items: center;
        .entry_icon {
          width: 52rpx;
          height: 52rpx;
          border-radius: 18rpx;
          margin-right: 14rpx;
          background: #eaf1ff;
          color: #2f6bff;
          font-size: 22rpx;
          font-weight: 600;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .order_word {
          opacity: 1;
          font-size: 28rpx;
          font-family: PingFangSC, PingFangSC-Regular;
          font-weight: 400;
          text-align: center;
          color: #1f2937;
          line-height: 40rpx;
        }
        .to_right {
          width: 24rpx;
          height: 24rpx;
          vertical-align: middle;
          position: absolute;
          top: 50%;
          right: 0;
          transform: translateY(-50%);
        }
      }
      .first_order {
        border-top: none;
      }
    }
</style>
