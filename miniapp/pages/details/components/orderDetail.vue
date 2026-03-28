<!-- 商品列表 -->
<template>
  <view class="box order_list_cont" v-if="Object.keys(orderDetailsData).length">
    <view class="order_list">
      <view class="word_text"><text class="word_style">{{ orderDetailsData.shopName || '订单商品' }}</text></view>
      <view class="order-type">
        <view class="type_item" v-for="(obj, index) in orderDataes" :key="index">
          <view class="dish_img">
            <image mode="aspectFill" :src="obj.image || '/static/imgDefault.png'" class="dish_img_url" @error="handleImageError(obj)"></image>
          </view>
          <view class="dish_info">
            <view class="dish_name">{{ obj.name }}</view>
            <view class="dish_dishFlavor">{{
              obj.specInfo ? obj.specInfo : ""
            }}</view>
            <view class="dish_price">
              ×
              <text v-if="obj.number && obj.number > 0" class="dish_number">{{
                obj.number
              }}</text>
            </view>
            <view class="dish_active">
              <text>￥</text>
              {{ Number(obj.amount || 0).toFixed(2) }}
            </view>
          </view>
        </view>
        <view class="iconUp">
          <view class="toggle_more" @click="showDisplay = !showDisplay" v-if="orderDetailsData.orderDetailList.length > 2">
            <text>{{ !showDisplay ? "展开更多" : "点击收起" }}</text>
            <image class="icon_img" :class="showDisplay ? 'icon_imgDown' : ''" src="../../../static/toRight.png" mode="">
            </image>
          </view>
        </view>
        <view class="orderList">
          <view class="totalMoney">
            合计
            <text class="text">
              <text>￥</text>
              {{ Number(orderDetailsData.amount || 0).toFixed(2) }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props: {
    // 商品数据
    orderDataes: {
      type: Array,
      default: () => [],
    },
    // 订单信息
    orderDetailsData: {
      type: Object,
      default: () => ({}),
    },
    // 是否显示展开收起
    showDisplay: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    handleImageError(obj) {
      if (obj && obj.image !== '/static/imgDefault.png') {
        this.$set(obj, 'image', '/static/imgDefault.png')
      }
    },
  },
};
</script>
<style src="../../order/style.scss" lang="scss"></style>