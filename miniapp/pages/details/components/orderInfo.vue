<!--订单信息-->
<template>
  <view class="box">
    <view class="orderBaseInfo">
      <view>
        <view>订单号码</view>
        <view>{{ orderDetailsData.number }}</view>
      </view>
      <view>
        <view>订单时间</view>
        <view>{{ orderDetailsData.orderTime }}</view>
      </view>
      <view>
        <view>备注信息</view>
        <view class="orderinfo-remak">{{
          orderDetailsData.remark ? orderDetailsData.remark : "暂无信息"
        }}</view>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props: {
    // 订单详情
    orderDetailsData: {
      type: Object,
      default: () => ({}),
    },
  },
};
</script>
<style src="../../order/style.scss" lang="scss"></style>