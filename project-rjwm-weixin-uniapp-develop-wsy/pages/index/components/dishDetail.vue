<!-- 商品详情弹层 -->
<template>
  <view class="dish_detail_pop">
    <image
      mode="aspectFill"
      class="div_big_image"
      :src="productDetails.image || '/static/imgDefault.png'"
      @error="handleImageError"
    ></image>
    <view class="title">{{ productDetails.name }}</view>
    <view class="desc">{{ productDetails.description || '暂无商品说明' }}</view>
    <view class="info_row" v-if="productDetails.brand || productDetails.model">
      <text v-if="productDetails.brand">品牌：{{ productDetails.brand }}</text>
      <text v-if="productDetails.model">型号：{{ productDetails.model }}</text>
    </view>
    <view class="info_row" v-if="productDetails.stock !== undefined && productDetails.stock !== null">
      <text>{{ Number(productDetails.stock) <= 0 ? '状态：暂时无货' : `库存：${productDetails.stock}` }}</text>
    </view>
    <view class="but_item">
      <view class="price">
        <text class="ico">￥</text>
        {{ Number(productDetails.price || 0).toFixed(2) }}
      </view>
      <view
        class="active"
        v-if="Number(productDetails.stock) > 0 && (!productDetails.flavors || productDetails.flavors.length === 0) && productDetails.dishNumber > 0"
      >
        <view
          class="dish_action_icon minus"
          @click="redDishAction(productDetails, '普通')"
        >-</view>
        <text class="dish_number">{{ productDetails.dishNumber }}</text>
        <view
          class="dish_action_icon plus"
          @click="addDishAction(productDetails, '普通')"
        >+</view>
      </view>

      <view class="active" v-if="Number(productDetails.stock) > 0 && productDetails.flavors && productDetails.flavors.length > 0"
        ><view class="dish_card_add" @click="moreNormDataesHandle(productDetails)"
          >选择规格</view
        ></view
      >
      <view
        class="active"
        v-if="
          Number(productDetails.stock) > 0 && productDetails.dishNumber === 0 && (!productDetails.flavors || productDetails.flavors.length === 0)
        "
      >
        <view class="dish_card_add" @click="addDishAction(productDetails, '普通')"
          >加入清单</view
        >
      </view>
    </view>
    <view class="stock_tip" v-if="Number(productDetails.stock) <= 0">当前商品暂时无货</view>
    <view class="close" @click="detailClose">
      <view class="close_img">×</view>
    </view>
  </view>
</template>
<script>
export default {
  // 获取父级传的数据
  props: {
    productDetails: {
      type: Object,
      default: () => ({}),
    },
    openDetailPop: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    // 加入购物车
    addDishAction(obj, item) {
      this.$emit("addDishAction", { obj: obj, item: item });
    },
    redDishAction(obj, item) {
      this.$emit("redDishAction", { obj: obj, item: item });
    },
    // 选择规格
    moreNormDataesHandle(obj) {
      this.$emit("moreNormDataesHandle", obj);
    },
    // 关闭商品详情
    detailClose() {
      this.$emit("detailClose");
    },
    handleImageError() {
      if (this.productDetails && this.productDetails.image !== '/static/imgDefault.png') {
        this.$set(this.productDetails, 'image', '/static/imgDefault.png')
      }
    },
  },
};
</script>
<style lang="scss" scoped>
.dish_detail_pop {
  width: calc(100vw - 160rpx);
  box-sizing: border-box;
  position: relative;
  top: 50%;
  left: 50%;
  padding: 40rpx;
  transform: translateX(-50%) translateY(-50%);
  background: #fff;
  border-radius: 20rpx;
  .div_big_image {
    width: 100%;
    height: 320rpx;
    border-radius: 10rpx;
  }
  .title {
    font-size: 40rpx;
    line-height: 80rpx;
    text-align: center;
    font-weight: bold;
  }
  .info_row {
    font-size: 24rpx;
    color: #666;
    line-height: 38rpx;
    display: flex;
    flex-direction: column;
    margin-top: 8rpx;
  }
  .but_item {
    display: flex;
    position: relative;
    flex: 1;
    .price {
      text-align: left;
      color: #2f6bff;
      line-height: 88rpx;
      box-sizing: border-box;
      font-size: 48rpx;
      font-weight: bold;
      .ico {
        font-size: 28rpx;
      }
    }
    .active {
      position: absolute;
      right: 0rpx;
      bottom: 20rpx;
      display: flex;
      .dish_action_icon {
        width: 64rpx;
        height: 64rpx;
        border-radius: 32rpx;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 40rpx;
        font-weight: 600;
        &.minus {
          background: #eef4ff;
          color: #2f6bff;
        }
        &.plus {
          background: #2f6bff;
          color: #ffffff;
        }
      }
      .dish_number {
        padding: 0 10rpx;
        line-height: 72rpx;
        font-size: 30rpx;
        font-family: PingFangSC, PingFangSC-Medium;
        font-weight: 500;
      }
      .dish_card_add {
        width: 200rpx;
        line-height: 60rpx;
        text-align: center;
        font-weight: 500;
        font-size: 28rpx;
        opacity: 1;
        background: #2f6bff;
        color: #ffffff;
        border-radius: 30rpx;
      }
    }
  }
  .stock_tip {
    margin-top: 24rpx;
    font-size: 24rpx;
    color: #999;
    text-align: right;
  }
}
.close {
  position: absolute;
  bottom: -180rpx;
  left: 50%;
  transform: translateX(-50%);
  .close_img {
    width: 88rpx;
    height: 88rpx;
    border-radius: 44rpx;
    background: rgba(255, 255, 255, 0.18);
    color: #ffffff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 52rpx;
    font-weight: 300;
  }
}
</style>
