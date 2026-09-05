<!-- 订单状态 -->
<template>
  <view>
    <view class="box">
      <view class="orderInfoTip">
        <view class="tit">{{ statusWord(orderDetailsData.status) }} <text class="smw"
            v-if="timeout && orderDetailsData.status === 1"> ( 已经超时)</text></view>
        <view class="rejectionReason" v-if="orderDetailsData.status === 7">
          <text v-if="orderDetailsData.cancelReason">{{ orderDetailsData.cancelReason }}</text>
          <text v-else-if="orderDetailsData.rejectionReason">{{ orderDetailsData.rejectionReason }}</text>
          <text v-else>订单正在售后处理中</text>
        </view>
        <view v-if="!timeout && orderDetailsData.status === 1">
          <view class="time">
            <view class="timeIcon"></view>
            订单保留时间：
            <text>{{ rocallTime }}</text>
          </view>
        </view>
        <view class="againBtn">
          <button class="new_btn" type="default" @click="handleCancel('center', orderDetailsData)" v-if="(!timeout && orderDetailsData.status === 1) ||
            orderDetailsData.status === 2 ||
            orderDetailsData.status === 3 ||
            orderDetailsData.status === 4
            ">
            取消订单
          </button>
          <button class="new_btn btn disabled-btn" type="default"
            v-if="!timeout && orderDetailsData.status === 1"
            disabled>
            暂未开放支付功能
          </button>
          <button class="new_btn" type="default" @click="handleRefund('center')" v-if="orderDetailsData.status == 5">
            售后处理
          </button>
          <button class="new_btn" type="default" @click="oneMoreOrder(orderDetailsData.id)"
            v-if="orderDetailsData.status !== 7">
            再来一单
          </button>
        </view>
      </view>
    </view>
    <view class="box timeTip" v-if="!timeout && orderDetailsData.status === 1">
      <view class="icon newIcon"></view>
      当前订单暂未开放支付功能，超时后系统可能自动取消。
    </view>
  </view>
</template>
<script>
import { statusWord } from "@/utils/index";
export default {
  // 获取父级传的数据
  props: {
    // 订单详情
    orderDetailsData: {
      type: Object,
      default: () => ({}),
    },
    // 倒计时间
    timeout: {
      type: Boolean,
      default: false,
    },
    // 倒计时
    rocallTime: {
      type: String,
      default: "",
    },
  },
  methods: {
    // 地址选择
    statusWord(status) {
      this.$emit("statusWord", status);
      // return errr;
      return statusWord(status);
    },
    //取消订单
    handleCancel(type, obj) {
      this.$emit("handleCancel", { type: type, obj: obj });
    },
    // 立即支付
    handlePay(id) {
      this.$emit("handlePay", id);
    },
    // 售后处理
    handleRefund(type) {
      this.$emit("handleRefund", type);
    },
    // 再来一单
    oneMoreOrder(id) {
      this.$emit("oneMoreOrder", id);
    },
  },
};
</script>
<style src="../../order/style.scss" lang="scss"></style>
<style scoped>
.disabled-btn {
  opacity: 0.5;
}
</style>