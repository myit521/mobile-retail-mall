<template>
  <view class="box">
    <view class="orderBaseInfo">
      <view>
        <view>门店信息</view>
        <view>
          <view class="nameInfo">
            <text>{{ orderDetailsData.userName || '下单用户' }}</text>
          </view>
          <view>{{ orderDetailsData.address || '门店地址待补充' }}</view>
        </view>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  props: {
    orderDetailsData: {
      type: Object,
      default: () => ({}),
    },
  },
};
</script>
<style src="../../order/style.scss" lang="scss"></style>
