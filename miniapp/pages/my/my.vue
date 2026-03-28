<template>
  <view>
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="个人中心"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#2F6BFF"
    ></uni-nav-bar>

    <view class="my-center">
      <!-- 头像展示部分 -->
      <head
        :psersonUrl="psersonUrl"
        :nickName="nickName"
        :gender="gender"
        :phoneNumber="phoneNumber"
        :getPhoneNum="getPhoneNum"
      ></head>

      <view class="container">
        <!-- 历史订单和手机型号推荐 -->
        <order-info @goOrder="goOrder" @goPhoneModel="goPhoneModel"></order-info>
        <!-- 最近订单 -->
        <!-- 最近订单title -->
        <view
          class="recent"
          v-if="recentOrdersList && recentOrdersList.length > 0"
        >
          <text class="order_line">最近订单</text>
        </view>
        <order-list
          :scrollH="scrollH"
          @lower="lower"
          @goDetail="goDetail"
          @oneOrderFun="oneOrderFun"
          @getOvertime="getOvertime"
          @statusWord="statusWord"
          @numes="numes"
          :loading="loading"
          :loadingText="loadingText"
          :recentOrdersList="recentOrdersList"
        ></order-list>
      </view>
    </view>
  </view>
</template>

<script>
import { getOrderPage, repetitionOrder, delShoppingCart } from "../api/api.js";
import { statusWord, getOvertime } from "@/utils/index.js";

import HeadInfo from "./components/headInfo.vue"; //头部
import OrderInfo from "./components/orderInfo.vue"; //地址
import OrderList from "./components/orderList.vue"; //最近订单
export default {
  data() {
    return {
      psersonUrl: "../../static/imgDefault.png",
      nickName: "",
      gender: "0",
      phoneNumber: "18500557668",
      recentOrdersList: [],
      sumOrder: {
        amount: 0,
        number: 0,
      },
      status: "",
      scrollH: 0,
      pageInfo: {
        page: 1,
        pageSize: 10,
        total: 0,
      },
      loadingText: "",
      loading: false,
    };
  },
  components: {
    HeadInfo,
    OrderInfo,
    OrderList,
  },
  filters: {
    getPhoneNum(str) {
      return str.replace(/\-/g, "");
    },
  },
  onLoad() {
    this.psersonUrl =
      this.$store.state.baseUserInfo &&
      this.$store.state.baseUserInfo.avatarUrl;
    this.nickName =
      this.$store.state.baseUserInfo && this.$store.state.baseUserInfo.nickName;
    this.gender =
      this.$store.state.baseUserInfo && this.$store.state.baseUserInfo.gender;
    this.phoneNumber =
      this.$store.state.shopPhone && this.$store.state.shopPhone;
    this.getList();
  },
  created() {},
  onReady() {
    uni.getSystemInfo({
      success: (res) => {
        this.scrollH = res.windowHeight - uni.upx2px(100);
      },
    });
  },
  methods: {
    statusWord(obj) {
      return statusWord(obj.status, obj.time);
    },
    getOvertime(time) {
      return getOvertime(time);
    },
    // 获取列表数据
    getList() {
      const normalizeImage = (image) => {
        if (!image || typeof image !== 'string') {
          return '/static/imgDefault.png';
        }
        const value = image.trim();
        if (!value || value.indexOf('/pages/') === 0 || value.indexOf('pages/') === 0) {
          return '/static/imgDefault.png';
        }
        if (value.indexOf('http://') === 0 || value.indexOf('https://') === 0) {
          return value;
        }
        if (value.indexOf('/') === 0) {
          return '/static/imgDefault.png';
        }
        return value;
      };
      const params = {
        pageSize: 10,
        page: this.pageInfo.page,
      };
      getOrderPage(params).then((res) => {
        if (res.code === 1) {
          const records = Array.isArray(res.data.records)
            ? res.data.records.map((record) => ({
                ...record,
                orderDetailList: Array.isArray(record.orderDetailList)
                  ? record.orderDetailList.map((detail) => ({
                      ...detail,
                      image: normalizeImage(detail.image),
                    }))
                  : [],
              }))
            : [];
          this.recentOrdersList = this.recentOrdersList.concat(records);
          this.pageInfo.total = res.data.total;
          this.loadingText = "";
          this.loading = false;
        }
      });
    },
    // 去历史订单页面
    goOrder() {
      // TODO
      uni.navigateTo({
        url: "/pages/historyOrder/historyOrder",
      });
    },
    // 去手机型号推荐页面
    goPhoneModel() {
      uni.navigateTo({
        url: "/pages/phoneModel/index",
      });
    },
    async oneOrderFun(id) {
      let pages = getCurrentPages();
      let routeIndex = pages.findIndex(
        (item) => item.route === "pages/index/index"
      );
      // 先清空购物车
      await delShoppingCart();
      repetitionOrder(id).then((res) => {
        if (res.code === 1) {
          uni.navigateBack({
            delta: routeIndex > -1 ? pages.length - routeIndex : 1,
          });
        }
      });
    },
    quitClick() {},
    // 去详情页面
    goDetail(id) {
      uni.redirectTo({
        url: "/pages/details/index?orderId=" + id,
      });
    },
    dataAdd() {
      const pages = Math.ceil(this.pageInfo.total / 10); //计算总页数
      if (this.pageInfo.page === pages) {
        this.loadingText = "没有更多了";
        this.loading = true;
      } else {
        this.pageInfo.page++;
        this.getList();
      }
    },

    lower() {
      this.loadingText = "数据加载中...";
      this.loading = true;
      this.dataAdd();
    },
    goBack() {
      uni.switchTab({
        url: "/pages/home/index",
      });
    },
  },
};
</script>
<style lang="scss" scoped>
.my-center {
  background: #f6f8fc;
  min-height: 100vh;

  .container {
    margin-top: 24rpx;
    min-height: calc(100% - 194rpx);
    padding-bottom: 40rpx;
    box-sizing: border-box;
  }
}

.recent {
  height: 60rpx;
  padding: 0 16rpx 0 22rpx;

  .order_line {
    opacity: 1;
    font-size: 28rpx;
    font-family: PingFangSC, PingFangSC-Medium;
    font-weight: 550;
    text-align: left;
    color: #1f2937;
    line-height: 60rpx;
    letter-spacing: 0;
    display: block;
    width: 100%;
    padding-left: 6rpx;
  }
}

::v-deep .uni-navbar--border {
  border-width: 0 !important;
}
</style>
