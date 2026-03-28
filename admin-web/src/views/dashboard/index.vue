<template>
  <div class="dashboard-container home">
    <!-- 营业数据 -->
    <Overview :overviewData="overviewData" />
    <!-- 订单管理 -->
    <Orderview :orderviewData="overviewData" />
    <div class="homeMain">
      <!-- 商品总览 -->
      <ProductStatistics :product-data="overviewData" />
      <!-- 库存预警 -->
      <StockAlert :stock-data="overviewData" />
    </div>
    <!-- 订单信息 -->
    <OrderList
      :order-statics="orderStatics"
      @getOrderListBy3Status="getOrderListBy3Status"
    />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { getOverview } from '@/api/index'
import { getMemoStats } from '@/api/memo'
import { getOrderListBy } from '@/api/order'
import Overview from './components/overview.vue'
import Orderview from './components/orderview.vue'
import ProductStatistics from './components/productStatistics.vue'
import StockAlert from './components/stockAlert.vue'
import OrderList from './components/orderList.vue'

@Component({
  name: 'Dashboard',
  components: {
    Overview,
    Orderview,
    ProductStatistics,
    StockAlert,
    OrderList,
  },
})
export default class extends Vue {
  private overviewData = {} as any
  private orderStatics = {} as any
  private memoStats = {} as any

  created() {
    this.init()
  }

  init() {
    this.$nextTick(() => {
      this.loadOverviewData()
      this.loadMemoStats()
      this.getOrderListBy3Status()
    })
  }

  // 加载数据概览（单接口获取所有工作台数据）
  async loadOverviewData() {
    try {
      const res = await getOverview()
      if (res.data.code === 1) {
        this.overviewData = {
          ...this.overviewData,
          ...(res.data.data || {}),
        }
      } else {
        this.$message.error(res.data.msg || '获取概览数据失败')
      }
    } catch (err) {
      this.$message.error('获取概览数据出错：' + (err.message || ''))
    }
  }

  async loadMemoStats() {
    try {
      const res = await getMemoStats()
      if (res.data.code === 1) {
        this.memoStats = res.data.data || {}
        this.overviewData = {
          ...this.overviewData,
          pendingMemoCount: this.memoStats.pendingCount || 0,
        }
      } else {
        this.$message.error(res.data.msg || '获取备忘录统计失败')
      }
    } catch (err) {
      this.$message.error('获取备忘录统计出错：' + ((err as any).message || ''))
    }
  }

  // 获取各状态订单数量
  getOrderListBy3Status() {
    getOrderListBy()
      .then((res: any) => {
        if (res.data.code === 1) {
          this.orderStatics = res.data.data
        } else {
          this.$message.error(res.data.msg)
        }
      })
      .catch((err: any) => {
        this.$message.error('请求出错了：' + err.message)
      })
  }
}
</script>

<style lang="scss">
</style>
