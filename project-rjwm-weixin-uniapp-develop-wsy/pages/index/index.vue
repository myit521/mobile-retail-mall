<template>
  <view>
    <!-- 导航 -->
    <navBar></navBar>
    <!-- end -->
    <view class="home_content" :style="{ paddingTop: ht + 'px' }" @touchmove.stop.prevent="disabledScroll">
      <view class="mall_search_box">
        <view class="mall_search_bar" @click="handleSearchClick">
          <view class="search_icon">搜</view>
          <view class="search_placeholder">搜索商品、品牌或型号</view>
        </view>
        <view class="mall_search_meta">
          <view class="mall_title">数码商城</view>
          <view class="businessStatus" v-if="shopStatus === 1">营业中</view>
          <view class="businessStatus close" v-else>暂停服务</view>
        </view>
      </view>
      <!-- 商品列表 -->
      <view class="restaurant_menu_list" v-if="shopStatus === 1 && typeListData.length > 0">
        <view class="type_list">
          <scroll-view scroll-y scroll-with-animation class="u-tab-view menu-scroll-view" :scroll-top="scrollTop + 100"
            :scroll-into-view="itemId">
            <view class="type_item" id="target" :class="[typeIndex == index ? 'active' : '']"
              v-for="(item, index) in typeListData" :key="index" @tap.stop="swichMenu(item, index)">
              <view class="item" :class="item.name.length > 5 ? 'allLine' : ''">{{ item.name }}</view>
            </view>
            <view class="seize_seat"></view>
          </scroll-view>
        </view>
        <scroll-view class="vegetable_order_list" scroll-y="true" scroll-top="0rpx"
          v-if="dishListItems && dishListItems.length > 0">
          <view class="type_item" :class="{ sold_out: Number(item.stock) <= 0 }" v-for="(item, index) in dishListItems" :key="index">
            <!-- 点击查看详情 -->
            <view class="dish_img" @click="openDetailHandle(item)">
              <image mode="aspectFill" :src="item.image" class="dish_img_url" @error="handleListImageError(item)"></image>
            </view>
            <view class="dish_info">
              <view class="dish_name" @click="openDetailHandle(item)">{{
                item.name
              }}</view>
              <view class="dish_label" @click="openDetailHandle(item)">{{
                item.description || item.brand || item.name
              }}</view>
              <view class="dish_label" @click="openDetailHandle(item)">
                {{ Number(item.stock) <= 0 ? '状态：暂时无货' : '库存：' + (item.stock !== undefined && item.stock !== null ? item.stock : '--') }}
              </view>
              <view class="dish_price">
                <text class="ico">￥</text>
                {{ Number(item.price || 0).toFixed(2) }}
              </view>
              <view class="dish_active" v-if="!item.flavors || item.flavors.length === 0">
                <view v-if="item.dishNumber >= 1" class="dish_action_icon minus" @click="redDishAction(item, '普通')">-</view>
                <text v-if="item.dishNumber > 0" class="dish_number">{{
                  item.dishNumber
                }}</text>
                <view v-if="Number(item.stock) > 0" class="dish_action_icon plus" @click="addDishAction(item, '普通')">+</view>
                <view v-else class="sold_out_btn">无货</view>
              </view>
              <view class="dish_active_btn" v-else>
                <view v-if="Number(item.stock) > 0" class="check_but" @click="moreNormDataesHandle(item)">选择规格</view>
                <view v-else class="check_but disabled_check_but">无货</view>
              </view>
            </view>
          </view>
          <view class="seize_seat"></view>
        </scroll-view>
        <view class="no_dish" v-else>
          <view v-if="typeListData.length > 0">该分类下暂无商品</view>
        </view>
      </view>
      <view class="no_dish category_empty" v-else-if="shopStatus === 1">
        <view>当前暂无可展示分类</view>
      </view>
      <view class="restaurant_close" v-else>店铺暂不可用</view>
      <!-- end -->
      <view class="mask-box"></view>
      <!-- 底部操作区 -->
      <!-- 清单为空时的状态 -->
      <view class="footer_order_buttom" v-if="orderListData().length === 0 || shopStatus !== 1">
        <view class="order_number order_number_placeholder">
          <view class="order_number_icon_text">单</view>
        </view>
        <view class="order_price">
          <text class="ico">￥</text>
          0
        </view>
        <view class="order_but disabled-btn">暂未开放支付功能</view>
      </view>
      <!-- end -->
      <!-- 清单已有商品时的状态 -->
      <view class="footer_order_buttom order_form" v-else>
        <view class="orderCar" @click="() => (openOrderCartList = !openOrderCartList)">
          <view class="order_number order_number_placeholder active">
            <view class="order_number_icon_text">单</view>
            <view class="order_dish_num">{{ orderDishNumber }}</view>
          </view>
          <view class="order_price">
            <text class="ico">￥</text>
            {{ Number(orderDishPrice || 0).toFixed(2) }}
          </view>
        </view>
        <view class="order_but disabled-btn">订单功能暂未开放</view>
      </view>
      <!-- end -->
      <!-- 选择多规格弹层 - start -->
      <view class="pop_mask" v-show="openMoreNormPop">
        <popMask :moreNormDishdata="moreNormDishdata" :moreNormdata="moreNormdata" :flavorDataes="flavorDataes"
          @checkMoreNormPop="checkMoreNormPop" @addShop="addShop" @closeMoreNorm="closeMoreNorm"></popMask>
      </view>
      <!-- 选择多规格 - end -->
      <!-- 商品详情弹层 - start -->
      <!-- openDetailHandle 这个函数触发的商品详情 -->
      <view class="pop_mask" v-show="openDetailPop" style="z-index: 9999">
        <product-detail :productDetails="productDetails" :openDetailPop="openDetailPop"
          @redDishAction="redDishAction" @addDishAction="addDishAction" @moreNormDataesHandle="moreNormDataesHandle"
          @detailClose="dishClose"></product-detail>
      </view>
      <!-- 商品详情 - end -->
      <!-- 商品清单弹框 - start -->
      <view class="pop_mask" v-show="openOrderCartList" @click="openOrderCartList = !openOrderCartList">
        <popCart :openOrderCartLis="openOrderCartList" :orderAndUserInfo="orderAndUserInfo"
          @clearCardOrder="clearCardOrder" @addDishAction="addDishAction" @redDishAction="redDishAction"></popCart>
      </view>
      <!-- 商品清单弹框 - end -->
      <view class="pop_mask" v-show="loaddingSt">
        <view class="lodding">
          <image class="lodding_ico" src="../../static/lodding.gif" mode=""></image>
        </view>
      </view>
      <!-- 电话弹层 -->
      <phone ref="phone" :phoneData="phoneData" @closePopup="closePopup"></phone>
      <!-- end -->
      <!-- 店铺暂停服务提示 -->
      <view class="colseShop" v-if="shopStatus === 0">
        <view class="shop">当前暂不可购买</view>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script src="./index.js"></script>
<style src="./style.scss" lang="scss" scoped></style>
<style scoped>
.disabled-btn {
  opacity: 0.5;
}

/* #ifdef MP-WEIXIN || APP-PLUS */
::v-deep ::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  -webkit-appearance: none;
  background: transparent;
  color: transparent;
}

/* #endif */
</style>
