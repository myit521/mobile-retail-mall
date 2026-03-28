<template>
  <view class="nonet_page">
    <uni-nav-bar title="网络异常" statusBar="true" fixed="true" color="#ffffff" backgroundColor="#2F6BFF" :border="false"></uni-nav-bar>
    <view class="nonet_content">
      <view class="state_card">
        <image class="success_icon" src="../../static/noNet.png" mode="aspectFit"></image>
        <view class="success_title">网络无法连接</view>
        <view class="success_desc">请检查当前网络状态后重试，恢复后可重新进入商城或推荐页。</view>
        <view class="go_dish" @click="goIndex()">返回首页</view>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  methods: {
    goIndex() {
      uni.switchTab({ url: "/pages/home/index" });
    },
  },
};
</script>
<style lang="scss" scoped>
.nonet_page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef4ff 0%, #f6f8fc 100%);
}

.nonet_content {
  min-height: 100vh;
  padding: 220rpx 32rpx 80rpx;
  box-sizing: border-box;
}

.state_card {
  background: #ffffff;
  border-radius: 32rpx;
  padding: 72rpx 48rpx;
  box-shadow: 0 18rpx 40rpx rgba(31, 41, 55, 0.08);
  text-align: center;
}

.success_icon {
  width: 260rpx;
  height: 260rpx;
}

.success_title {
  margin-top: 12rpx;
  font-size: 42rpx;
  font-weight: 700;
  color: #1f2937;
}

.success_desc {
  margin-top: 20rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #6b7280;
}

.go_dish {
  margin: 40rpx auto 0;
  width: 240rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  background: #2f6bff;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
