<template>
  <view class="search_page">
    <view class="search_header">
      <view class="search_box">
        <input
          class="search_input"
          :value="keyword"
          placeholder="搜索商品、品牌或型号"
          confirm-type="search"
          @input="handleInput"
          @confirm="handleConfirm"
        />
      </view>
      <view class="search_action" @click="handleConfirm">搜索</view>
    </view>

    <view class="history_card" v-if="historyList.length > 0 && !keyword.trim()">
      <view class="section_header">
        <text>搜索历史</text>
        <text class="clear_text" @click="clearHistory">清空</text>
      </view>
      <view class="tag_list">
        <view
          class="tag_item"
          v-for="item in historyList"
          :key="item"
          @click="useHistory(item)"
        >
          {{ item }}
        </view>
      </view>
    </view>

    <view class="history_card" v-if="hotKeywords.length > 0 && !keyword.trim()">
      <view class="section_header">
        <text>热门搜索</text>
      </view>
      <view class="tag_list">
        <view
          class="tag_item hot_tag_item"
          v-for="item in hotKeywords"
          :key="item"
          @click="useHotKeyword(item)"
        >
          {{ item }}
        </view>
      </view>
    </view>

    <view class="result_state" v-if="loading">正在搜索...</view>
    <view class="result_state" v-else-if="hasSearched && resultList.length === 0">没有找到相关商品</view>

    <scroll-view class="result_list" scroll-y="true" v-else-if="resultList.length > 0">
      <view class="result_item" v-for="item in resultList" :key="item.id" @click="selectProduct(item)">
        <image class="item_image" :src="item.image" mode="aspectFill"></image>
        <view class="item_content">
          <view class="item_name">
            <text
              v-for="(part, index) in getHighlightParts(item.name)"
              :key="`name-${item.id}-${index}`"
              :class="{ highlight_text: part.highlight }"
            >
              {{ part.text }}
            </text>
          </view>
          <view class="item_desc">
            <text
              v-for="(part, index) in getHighlightParts(buildDesc(item))"
              :key="`desc-${item.id}-${index}`"
              :class="{ highlight_text: part.highlight }"
            >
              {{ part.text }}
            </text>
          </view>
          <view class="item_meta">
            <text v-if="item.categoryName">{{ item.categoryName }}</text>
            <text v-if="item.specList && item.specList.length > 0">可选规格</text>
            <text v-if="item.stock !== null && item.stock !== undefined">库存 {{ item.stock }}</text>
          </view>
          <view class="item_footer">
            <view class="item_price">￥{{ formatPrice(item.price) }}</view>
            <view class="item_link">查看商品</view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { searchProducts } from "../api/api.js"

const SEARCH_HISTORY_KEY = "mall-search-history"

export default {
  data() {
    return {
      keyword: "",
      resultList: [],
      historyList: [],
      hotKeywords: ["iPhone", "华为", "小米", "手机壳", "充电器", "钢化膜"],
      loading: false,
      hasSearched: false,
      debounceTimer: null,
    }
  },
  onLoad() {
    this.loadHistory()
  },
  onUnload() {
    this.clearDebounce()
  },
  methods: {
    handleInput(event) {
      this.keyword = event.detail.value || ""
      this.clearDebounce()
      if (!this.keyword.trim()) {
        this.resultList = []
        this.hasSearched = false
        return
      }
      this.debounceTimer = setTimeout(() => {
        this.runSearch(this.keyword)
      }, 300)
    },
    handleConfirm() {
      this.clearDebounce()
      this.runSearch(this.keyword)
    },
    async runSearch(keyword) {
      const value = (keyword || "").trim()
      if (!value) {
        this.resultList = []
        this.hasSearched = false
        return
      }

      this.loading = true
      try {
        const res = await searchProducts({ keyword: value })
        this.resultList = Array.isArray(res.data)
          ? res.data.map((item) => ({
              ...item,
              image: this.normalizeProductImage(item.image),
            }))
          : []
        this.hasSearched = true
        this.saveHistory(value)
      } catch (error) {
        this.resultList = []
        this.hasSearched = true
        uni.showToast({
          title: "搜索失败，请稍后再试",
          icon: "none",
        })
      } finally {
        this.loading = false
      }
    },
    selectProduct(product) {
      const eventChannel = this.getOpenerEventChannel()
      eventChannel.emit("selectProduct", product)
      uni.navigateBack()
    },
    useHistory(keyword) {
      this.keyword = keyword
      this.runSearch(keyword)
    },
    useHotKeyword(keyword) {
      this.keyword = keyword
      this.runSearch(keyword)
    },
    loadHistory() {
      const history = uni.getStorageSync(SEARCH_HISTORY_KEY)
      this.historyList = Array.isArray(history) ? history : []
    },
    saveHistory(keyword) {
      const nextHistory = [keyword, ...this.historyList.filter((item) => item !== keyword)].slice(0, 10)
      this.historyList = nextHistory
      uni.setStorageSync(SEARCH_HISTORY_KEY, nextHistory)
    },
    clearHistory() {
      this.historyList = []
      uni.removeStorageSync(SEARCH_HISTORY_KEY)
    },
    clearDebounce() {
      if (this.debounceTimer) {
        clearTimeout(this.debounceTimer)
        this.debounceTimer = null
      }
    },
    normalizeProductImage(image) {
      if (!image || typeof image !== "string") {
        return "/static/imgDefault.png"
      }
      const value = image.trim()
      if (!value) {
        return "/static/imgDefault.png"
      }
      if (value.indexOf("http://") === 0 || value.indexOf("https://") === 0) {
        return value
      }
      if (value.indexOf("/pages/") === 0 || value.indexOf("pages/") === 0 || value.indexOf("/") === 0) {
        return "/static/imgDefault.png"
      }
      return value
    },
    buildDesc(item) {
      return [item.brand, item.model, item.description].filter(Boolean).join(" / ") || "暂无商品说明"
    },
    getHighlightParts(text) {
      const content = text || ""
      const keyword = (this.keyword || "").trim()
      if (!content || !keyword) {
        return [{ text: content, highlight: false }]
      }

      const lowerContent = content.toLowerCase()
      const lowerKeyword = keyword.toLowerCase()
      const parts = []
      let start = 0
      let index = lowerContent.indexOf(lowerKeyword)

      while (index !== -1) {
        if (index > start) {
          parts.push({
            text: content.slice(start, index),
            highlight: false,
          })
        }
        parts.push({
          text: content.slice(index, index + keyword.length),
          highlight: true,
        })
        start = index + keyword.length
        index = lowerContent.indexOf(lowerKeyword, start)
      }

      if (start < content.length) {
        parts.push({
          text: content.slice(start),
          highlight: false,
        })
      }

      return parts.length > 0 ? parts : [{ text: content, highlight: false }]
    },
    formatPrice(price) {
      return Number(price || 0).toFixed(2)
    },
  },
}
</script>

<style lang="scss" scoped>
.search_page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

.search_header {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.search_box {
  flex: 1;
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 10rpx 28rpx rgba(31, 41, 55, 0.08);
  padding: 0 24rpx;
}

.search_input {
  height: 88rpx;
  font-size: 28rpx;
  color: #1f2937;
}

.search_action {
  width: 120rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: #2f6bff;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.history_card {
  margin-top: 28rpx;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx;
}

.section_header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 28rpx;
  color: #1f2937;
  font-weight: 600;
}

.clear_text {
  font-size: 24rpx;
  color: #7c8798;
  font-weight: 400;
}

.tag_list {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
  margin-top: 24rpx;
}

.tag_item {
  padding: 14rpx 24rpx;
  border-radius: 999rpx;
  background: #eef4ff;
  color: #2f6bff;
  font-size: 24rpx;
}

.hot_tag_item {
  background: #fff4e8;
  color: #d97706;
}

.result_state {
  padding-top: 160rpx;
  text-align: center;
  color: #7c8798;
  font-size: 28rpx;
}

.result_list {
  height: calc(100vh - 160rpx);
  margin-top: 24rpx;
}

.result_item {
  display: flex;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.item_image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 18rpx;
  background: #eef2f7;
  flex-shrink: 0;
}

.item_content {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.item_name {
  font-size: 30rpx;
  color: #1f2937;
  font-weight: 600;
  line-height: 42rpx;
}

.item_desc {
  font-size: 24rpx;
  color: #7c8798;
  line-height: 36rpx;
  margin-top: 10rpx;
}

.highlight_text {
  color: #2f6bff;
  font-weight: 700;
}

.item_meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;

  text {
    padding: 6rpx 14rpx;
    border-radius: 999rpx;
    background: #f2f4f8;
    color: #4b5563;
    font-size: 22rpx;
  }
}

.item_footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
}

.item_price {
  color: #2f6bff;
  font-size: 34rpx;
  font-weight: 700;
}

.item_link {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: #2f6bff;
  color: #ffffff;
  font-size: 24rpx;
}
</style>
