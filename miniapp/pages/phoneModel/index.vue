<template>
  <view class="phone-model-page">
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="手机型号推荐"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#2F6BFF"
    ></uni-nav-bar>

    <view class="page-body">
      <view class="detect-card card">
        <view class="card-title">当前设备识别</view>
        <view class="detect-row">
          <text class="label">识别结果</text>
          <text class="value">{{ detectedModelText }}</text>
        </view>
        <view class="detect-row">
          <text class="label">匹配状态</text>
          <text class="value" :class="matchedPhoneModel ? 'success' : 'warning'">
            {{ matchedPhoneModel ? '已匹配到推荐机型' : '未自动匹配，请手动选择' }}
          </text>
        </view>
        <view class="detect-actions">
          <view class="action-btn primary" @click="detectPhoneModel">重新识别</view>
          <view class="action-btn" @click="useDetectedModel" v-if="matchedPhoneModel">使用识别结果</view>
        </view>
      </view>

      <view class="selector-card card">
        <view class="card-title">手动选择机型</view>
        <view class="selector-group">
          <view class="selector-label">品牌</view>
          <scroll-view scroll-x class="tag-scroll" enable-flex>
            <view class="tag-list">
              <view
                v-for="brand in brandOptions"
                :key="brand"
                class="tag-item"
                :class="{ active: selectedBrand === brand }"
                @click="selectBrand(brand)"
              >
                {{ brand }}
              </view>
            </view>
          </scroll-view>
        </view>

        <view class="selector-group" v-if="selectedBrand">
          <view class="selector-label">型号</view>
          <view v-if="loadingModels" class="empty-tip">型号加载中...</view>
          <view v-else-if="modelOptions.length === 0" class="empty-tip">当前品牌暂无可选型号</view>
          <view v-else class="model-list">
            <view
              v-for="item in modelOptions"
              :key="item.id"
              class="model-item"
              :class="{ active: selectedPhoneModelId === item.id }"
              @click="selectPhoneModel(item)"
            >
              <view class="model-name">{{ item.modelName }}</view>
              <view class="model-brand">{{ item.brand }}</view>
            </view>
          </view>
        </view>
      </view>

      <view class="result-card card">
        <view class="card-title">推荐商品</view>
        <view class="result-summary" v-if="selectedPhoneModelName">
          当前推荐机型：{{ selectedPhoneModelName }}
        </view>
        <view v-if="loadingProducts" class="empty-tip">推荐商品加载中...</view>
        <view v-else-if="!selectedPhoneModelId" class="empty-tip">请选择手机品牌和型号后查看推荐商品</view>
        <view v-else-if="productList.length === 0" class="empty-tip">当前机型暂未配置推荐商品</view>
        <view v-else class="product-list">
          <view class="product-item" v-for="item in productList" :key="item.id">
            <image class="product-image" :src="item.image || '/static/imgDefault.png'" mode="aspectFill" @error="handleProductImageError(item)"></image>
            <view class="product-info">
              <view class="product-name">{{ item.name }}</view>
              <view class="product-desc">{{ item.description || item.brand || '暂无商品说明' }}</view>
              <view class="product-meta">库存：{{ getStockText(item) }}</view>
              <view class="product-price">￥{{ Number(item.price || 0).toFixed(2) }}</view>
              <view class="product-actions">
                <view class="product-action-btn" @click="goHome">去首页选购</view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getPhoneModelList,
  getPhoneModelListByBrand,
  getProductListByPhoneModel,
} from "../api/api.js";

export default {
  data() {
    return {
      rawDetectedModel: "",
      normalizedDetectedModel: "",
      phoneModelList: [],
      brandOptions: [],
      selectedBrand: "",
      modelOptions: [],
      selectedPhoneModelId: "",
      selectedPhoneModelName: "",
      matchedPhoneModel: null,
      productList: [],
      loadingModels: false,
      loadingProducts: false,
    };
  },
  computed: {
    detectedModelText() {
      return this.rawDetectedModel || "暂未识别到机型";
    },
  },
  onLoad() {
    this.initPage();
  },
  methods: {
    async initPage() {
      await this.loadPhoneModelList();
      this.detectPhoneModel();
    },
    async loadPhoneModelList() {
      try {
        const res = await getPhoneModelList();
        if (res.code === 1) {
          const list = Array.isArray(res.data) ? res.data : [];
          this.phoneModelList = list;
          this.brandOptions = [...new Set(list.map((item) => item.brand).filter(Boolean))];
        } else {
          this.phoneModelList = [];
          this.brandOptions = [];
        }
      } catch (e) {
        this.phoneModelList = [];
        this.brandOptions = [];
        uni.showToast({
          title: "机型列表加载失败",
          icon: "none",
        });
      }
    },
    detectPhoneModel() {
      let model = "";
      try {
        if (typeof wx !== "undefined" && wx.getDeviceInfo) {
          const deviceInfo = wx.getDeviceInfo();
          model = deviceInfo && deviceInfo.model ? deviceInfo.model : "";
        }
      } catch (e) {}

      if (!model) {
        try {
          const systemInfo = uni.getSystemInfoSync();
          model = systemInfo && systemInfo.model ? systemInfo.model : "";
        } catch (e) {}
      }

      this.rawDetectedModel = model || "unknown";
      this.normalizedDetectedModel = this.normalizeModelName(this.rawDetectedModel);
      this.tryMatchDetectedModel();
    },
    normalizeModelName(model) {
      return String(model || "")
        .replace(/[（(].*?[）)]/g, "")
        .replace(/<.*?>/g, "")
        .replace(/[^a-zA-Z0-9+\-\s]/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .toLowerCase();
    },
    isStrictModelMatch(detectedModel, targetModel) {
      if (!detectedModel || !targetModel) {
        return false;
      }
      if (detectedModel === targetModel) {
        return true;
      }
      if (detectedModel.length < targetModel.length) {
        return false;
      }
      return detectedModel.startsWith(targetModel + " ") || detectedModel.endsWith(" " + targetModel);
    },
    tryMatchDetectedModel() {
      if (!this.normalizedDetectedModel || this.normalizedDetectedModel === "unknown") {
        this.matchedPhoneModel = null;
        return;
      }

      const exactMatch = this.phoneModelList.find((item) => {
        const normalizedName = this.normalizeModelName(item.modelName);
        return this.isStrictModelMatch(this.normalizedDetectedModel, normalizedName);
      });

      if (exactMatch) {
        this.matchedPhoneModel = exactMatch;
        this.selectedBrand = exactMatch.brand;
        this.loadBrandModels(exactMatch.brand, exactMatch.id);
      } else {
        this.matchedPhoneModel = null;
      }
    },
    async useDetectedModel() {
      if (!this.matchedPhoneModel) {
        return;
      }
      this.selectedBrand = this.matchedPhoneModel.brand;
      await this.loadBrandModels(this.selectedBrand, this.matchedPhoneModel.id);
    },
    async selectBrand(brand) {
      if (this.selectedBrand === brand) {
        return;
      }
      this.selectedBrand = brand;
      this.selectedPhoneModelId = "";
      this.selectedPhoneModelName = "";
      this.productList = [];
      await this.loadBrandModels(brand);
    },
    async loadBrandModels(brand, selectedId) {
      this.loadingModels = true;
      this.modelOptions = [];
      try {
        const res = await getPhoneModelListByBrand({ brand });
        if (res.code === 1) {
          this.modelOptions = Array.isArray(res.data) ? res.data : [];
          if (selectedId) {
            const current = this.modelOptions.find((item) => item.id === selectedId);
            if (current) {
              this.selectPhoneModel(current);
            }
          }
        } else {
          this.modelOptions = [];
        }
      } catch (e) {
        this.modelOptions = [];
        uni.showToast({
          title: "型号列表加载失败",
          icon: "none",
        });
      } finally {
        this.loadingModels = false;
      }
    },
    async selectPhoneModel(item) {
      this.selectedPhoneModelId = item.id;
      this.selectedPhoneModelName = item.modelName;
      await this.loadProducts(item.id);
    },
    async loadProducts(phoneModelId) {
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
      this.loadingProducts = true;
      this.productList = [];
      try {
        const res = await getProductListByPhoneModel({ phoneModelId });
        if (res.code === 1) {
          this.productList = Array.isArray(res.data)
            ? res.data.map((item) => ({
                ...item,
                image: normalizeImage(item.image),
              }))
            : [];
        } else {
          this.productList = [];
        }
      } catch (e) {
        this.productList = [];
        uni.showToast({
          title: "推荐商品加载失败",
          icon: "none",
        });
      } finally {
        this.loadingProducts = false;
      }
    },
    handleProductImageError(item) {
      if (item && item.image !== '/static/imgDefault.png') {
        this.$set(item, 'image', '/static/imgDefault.png')
      }
    },
    getStockText(item) {
      if (item && item.stock !== undefined && item.stock !== null && item.stock !== "") {
        return item.stock;
      }
      return "--";
    },
    goHome() {
      uni.switchTab({
        url: "/pages/index/index",
        fail: () => {
          uni.redirectTo({
            url: "/pages/index/index",
          });
        },
      });
    },
    goBack() {
      uni.navigateBack({
        delta: 1,
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.phone-model-page {
  min-height: 100vh;
  background: #f6f8fc;
}

.page-body {
  padding: 108rpx 24rpx 24rpx;
}

.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 12rpx 28rpx rgba(31, 41, 55, 0.05);
}

.card-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 24rpx;
}

.detect-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
  font-size: 26rpx;
}

.label {
  color: #6b7280;
}

.value {
  color: #1f2937;
  text-align: right;
}

.success {
  color: #19be6b;
}

.warning {
  color: #2563eb;
}

.detect-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.action-btn {
  flex: 1;
  text-align: center;
  line-height: 76rpx;
  border-radius: 38rpx;
  border: 1rpx solid #dbe4f3;
  color: #1f2937;
  background: #ffffff;
  font-size: 26rpx;
}

.action-btn.primary {
  background: #2f6bff;
  border-color: #2f6bff;
  color: #ffffff;
}

.selector-group {
  margin-bottom: 24rpx;
}

.selector-label {
  font-size: 28rpx;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 16rpx;
}

.tag-scroll {
  white-space: nowrap;
}

.tag-list {
  display: inline-flex;
  gap: 16rpx;
}

.tag-item {
  padding: 14rpx 28rpx;
  background: #eef2f7;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.tag-item.active {
  background: #eaf1ff;
  color: #2f6bff;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.model-item {
  border: 1rpx solid #e5e7eb;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  background: #ffffff;
}

.model-item.active {
  border-color: #2f6bff;
  background: #eef4ff;
}

.model-name {
  font-size: 28rpx;
  color: #1f2937;
  font-weight: 500;
}

.model-brand {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9aa3b2;
}

.result-summary {
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 20rpx;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.product-item {
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  border-radius: 18rpx;
  background: #f8fbff;
}

.product-image {
  width: 168rpx;
  height: 168rpx;
  border-radius: 16rpx;
  background: #edf2f7;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-size: 30rpx;
  color: #1f2937;
  font-weight: 600;
}

.product-desc,
.product-meta {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.product-actions {
  margin-top: 18rpx;
}

.product-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 180rpx;
  height: 60rpx;
  padding: 0 24rpx;
  border-radius: 30rpx;
  background: #eaf1ff;
  color: #2f6bff;
  font-size: 24rpx;
}

.product-price {
  margin-top: 16rpx;
  font-size: 30rpx;
  color: #2f6bff;
  font-weight: 600;
}

.empty-tip {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #9aa3b2;
}
</style>
