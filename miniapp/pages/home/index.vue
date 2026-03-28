<template>
  <view class="home-page">
    <uni-nav-bar
      title="首页"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#2F6BFF"
      :border="false"
    ></uni-nav-bar>

    <view class="page-body">
      <view class="hero-section">
        <view class="hero-tag">HOME</view>
        <view class="hero-title">数码零售商城</view>
        <view class="hero-subtitle">快速进入商城浏览商品，或按手机型号查看推荐商品</view>
      </view>

      <view class="notice-section">
        <view class="notice-card">
          <view class="notice-title">通知 / 广告位</view>
          <view class="notice-desc">当前预留中，后续可接入公告、活动与轮播内容</view>
        </view>
      </view>

      <view class="entry-section">
        <view class="entry-card mall-card" @click="goMall">
          <view class="entry-icon">商城</view>
          <view class="entry-label">进入商城</view>
          <view class="entry-desc">浏览商品与分类清单</view>
        </view>
        <view class="entry-card phone-card" @click="goPhoneModel">
          <view class="entry-icon">机型</view>
          <view class="entry-label">手机型号推荐</view>
          <view class="entry-desc">根据机型查看推荐商品</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { userLogin } from "../api/api.js";
import { mapState, mapMutations } from "vuex";

export default {
  data() {
    return {
      loginPending: false,
    };
  },
  computed: {
    ...mapState(["token"]),
  },
  methods: {
    ...mapMutations(["setBaseUserInfo", "setToken", "setShopInfo"]),
    goMall() {
      this.ensureWechatLogin(() => {
        uni.switchTab({
          url: "/pages/index/index",
        });
      });
    },
    goPhoneModel() {
      this.ensureWechatLogin(() => {
        uni.navigateTo({
          url: "/pages/phoneModel/index",
        });
      });
    },
    ensureWechatLogin(onSuccess) {
      if (this.token) {
        typeof onSuccess === "function" && onSuccess();
        return;
      }
      if (this.loginPending) {
        return;
      }
      this.loginPending = true;
      this.handleWechatLogin(onSuccess);
    },
    getLoginLocation() {
      return new Promise((resolve, reject) => {
        uni.getLocation({
          type: "gcj02",
          isHighAccuracy: true,
          success: (result) => {
            if (process.env.NODE_ENV === '"development"') {
              resolve("116.481488,39.990464");
              return;
            }
            resolve(`${result.longitude},${result.latitude}`);
          },
          fail: reject,
        });
      });
    },
    handleWechatLogin(onSuccess) {
      uni.getUserProfile({
        desc: "用于完善登录信息",
        success: async (userInfoRes) => {
          try {
            this.setBaseUserInfo(userInfoRes.userInfo);
            const code = await new Promise((resolve, reject) => {
              uni.login({
                provider: "weixin",
                success: (loginRes) => {
                  if (loginRes.errMsg === "login:ok" && loginRes.code) {
                    resolve(loginRes.code);
                    return;
                  }
                  reject(new Error("微信登录失败"));
                },
                fail: reject,
              });
            });
            const location = await this.getLoginLocation();
            const success = await userLogin({ code, location });
            if (success.code === 1) {
              this.setToken(success.data.token);
              this.setShopInfo({
                shopName: success.data.shopName,
                shopAddress: success.data.shopAddress,
                shopId: success.data.shopId,
              });
              typeof onSuccess === "function" && onSuccess();
              return;
            }
            uni.showToast({
              title: success.msg || "登录失败",
              icon: "none",
            });
          } catch (error) {
            const title = error && error.errMsg && error.errMsg.indexOf("getLocation:fail") > -1
              ? "获取地理位置失败"
              : "微信登录失败";
            uni.showToast({
              title,
              icon: "none",
            });
          } finally {
            this.loginPending = false;
          }
        },
        fail: (error) => {
          this.loginPending = false;
          const title = error && error.errMsg && error.errMsg.indexOf("auth deny") > -1
            ? "你已取消微信授权"
            : "微信授权失败";
          uni.showToast({
            title,
            icon: "none",
          });
        },
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eaf1ff 0%, #f6f8fc 35%, #f6f8fc 100%);
}

.page-body {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 118rpx 28rpx 84rpx;
  display: flex;
  flex-direction: column;
}

.hero-section {
  padding: 28rpx 8rpx 0;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(47, 107, 255, 0.12);
  color: #2f6bff;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.hero-title {
  margin-top: 18rpx;
  font-size: 46rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.4;
}

.hero-subtitle {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.7;
}

.notice-section {
  margin-top: 40rpx;
}

.notice-card {
  min-height: 196rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 14rpx 32rpx rgba(31, 41, 55, 0.06);
  padding: 32rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.notice-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2937;
}

.notice-desc {
  margin-top: 14rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.entry-section {
  margin-top: 180rpx;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 24rpx;
}

.entry-card {
  flex: 1;
  min-height: 316rpx;
  border-radius: 32rpx;
  padding: 34rpx 28rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 18rpx 40rpx rgba(31, 41, 55, 0.08);
}

.mall-card {
  background: linear-gradient(180deg, #ffffff 0%, #eaf1ff 100%);
}

.phone-card {
  background: linear-gradient(180deg, #ffffff 0%, #eef4ff 100%);
}

.entry-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: linear-gradient(180deg, #2f6bff 0%, #1e4fd1 100%);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.entry-label {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.4;
}

.entry-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}
</style>
