<!--我的头部-->
<template>
    <view class="my_info">
        <!-- 头像部分 -->
        <view class="head">
          <image class="head_image" :src="psersonUrl"></image>
        </view>
        <!-- 姓名及手机号 -->
        <view class="phone_name">
          <!-- 姓名 -->
          <view class="name">
            <text class="name_text">{{ nickName }}</text>
            <image
              v-if="gender === 2"
              class="name_type"
              src="../../../static/girl.png"
            ></image>
            <image
              v-if="gender === 1"
              class="name_type"
              src="../../../static/boy.png"
            ></image>
          </view>
          <!-- 电话号 -->
          <view class="phone">
            <text class="phone_text">{{ phoneNumber | getPhoneNum }}</text>
          </view>
        </view>
      </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props: {
    // 头像
    psersonUrl: {
      type: String,
      default: '',
    },
    // 姓名
    nickName: {
      type: String,
      default: '',
    },
    // 性别
    gender: {
      type: String,
      default: '',
    },
    // 电话
    phoneNumber: {
      type: String,
      default: '',
    },
    // 电话
    getPhoneNum: {
      type: String,
      default: '',
    }
  },
};
</script>
<style lang="scss" scoped>
.my_info {
    width: 710rpx;
    min-height: 188rpx;
    margin: 24rpx auto 0;
    padding: 28rpx;
    border-radius: 24rpx;
    background: linear-gradient(135deg, #2f6bff 0%, #1e4fd1 100%);
    box-shadow: 0 16rpx 36rpx rgba(31, 41, 55, 0.12);
    display: flex;
    box-sizing: border-box;
    .head {
      width: 140rpx;
      height: 140rpx;
      margin: auto 0;
      text-align: center;
      .head_image {
        width: 116rpx;
        height: 116rpx;
        vertical-align: top;
        margin: 12rpx auto;
        border-radius: 50%;
        background-color: #fff;
        border: 6rpx solid rgba(255, 255, 255, 0.2);
      }
    }
    .phone_name {
      flex: 1;
      margin: auto 0;
      .name {
        .name_text {
          font-size: 32rpx;
          opacity: 1;
          font-family: PingFangSC, PingFangSC-Medium;
          font-weight: 550;
          text-align: left;
          color: #ffffff;
          height: 44rpx;
          line-height: 44rpx;
          margin-right: 12rpx;
        }

        .name_type {
          width: 32rpx;
          height: 32rpx;
          vertical-align: middle;
          margin-bottom: 6rpx;
        }
      }
      .phone {
        margin-top: 12rpx;
        .phone_text {
          display: inline-flex;
          align-items: center;
          min-height: 40rpx;
          padding: 6rpx 18rpx;
          border-radius: 999rpx;
          font-size: 24rpx;
          font-family: PingFangSC, PingFangSC-Regular;
          font-weight: 400;
          text-align: left;
          color: #dbe7ff;
          line-height: 40rpx;
          background: rgba(255, 255, 255, 0.12);
        }
      }
    }
  }
</style>
