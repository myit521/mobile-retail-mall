<template>
  <view class="success_page">
    <uni-nav-bar title="订单流程提示" statusBar="true" fixed="true" color="#ffffff" backgroundColor="#2F6BFF" :border="false"></uni-nav-bar>
    <view class="success_content">
      <view class="success_card">
        <image class="success_icon" src="../../static/success.png" mode="aspectFit"></image>
        <view class="success_title">页面已保留</view>
        <view class="word-box">
          <text class="word_bottom">当前版本暂未开放支付功能</text>
        </view>
        <view class="success_desc">后续完善支付能力后，再启用完整订单流程。</view>
        <view class="btns">
          <view class="go_dish" @click="goIndex()">返回首页</view>
        </view>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  data() {
    return {
      arrivalTime: "",
      orderId: null,
    };
  },
  onLoad(options) {
    this.orderId = options.orderId;
  },
  methods: {
    goIndex() {
      uni.switchTab({
        url: "/pages/home/index",
      });
    },
  },
};
</script>
<style lang="scss" scoped>
.success_page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef4ff 0%, #f6f8fc 100%);
}

.success_content {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 220rpx 32rpx 80rpx;
}

.success_card {
  text-align: center;
  background: #ffffff;
  border-radius: 32rpx;
  padding: 72rpx 48rpx;
  box-shadow: 0 18rpx 40rpx rgba(31, 41, 55, 0.08);
}

.success_icon {
  width: 144rpx;
  height: 138rpx;
}

.success_title {
  font-size: 40rpx;
  font-family: PingFangSC, PingFangSC-Medium;
  font-weight: 600;
  color: #1f2937;
  margin-top: 24rpx;
  line-height: 56rpx;
}

.success_desc {
  font-size: 28rpx;
  font-family: PingFangSC, PingFangSC-Regular;
  font-weight: 400;
  text-align: center;
  color: #6b7280;
  line-height: 46rpx;
  margin-top: 24rpx;
}

.word-box {
  margin-top: 32rpx;
}

.word_bottom {
  font-family: PingFangSC, PingFangSC-Regular;
  font-weight: 400;
  text-align: center;
  font-size: 30rpx;
  color: #4b5563;
}

.btns {
  display: flex;
  justify-content: center;
}

.go_dish {
  font-size: 30rpx;
  width: 220rpx;
  height: 80rpx;
  line-height: 80rpx;
  margin-top: 40rpx;
  background: #2f6bff;
  color: #ffffff;
  border-radius: 40rpx;
  font-weight: 600;
}
</style>
