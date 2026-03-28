<template>
  <div class="dashboard-container home">
    <!-- 标题 -->
    <TitleIndex @sendTitleInd="getTitleNum" :flag="flag" :tateData="tateData" />
    <div class="homeMain">
      <!-- 营业额统计 -->
      <TurnoverStatistics :turnoverdata="turnoverData" />
      <!-- 库存预警统计 -->
      <LowStockStatistics :alertdata="alertData" />
    </div>
    <div class="homeMain homecon">
      <!-- 订单统计 -->
      <OrderStatistics :orderdata="orderData" :overviewData="overviewData" />
      <!-- 销量排名TOP10 -->
      <Top :top10data="top10Data" />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  get1stAndToday,
  past7Day,
  past30Day,
  pastWeek,
  pastMonth,
} from '@/utils/formValidate'
import {
  getTurnoverStatistics,
  getOrderStatistics,
  getSalesRanking,
} from '@/api/index'
import { getStockAlerts } from '@/api/stock'
// 组件
import TitleIndex from './components/titleIndex.vue'
import TurnoverStatistics from './components/turnoverStatistics.vue'
import LowStockStatistics from './components/lowStockStatistics.vue'
import OrderStatistics from './components/orderStatistics.vue'
import Top from './components/top10.vue'

@Component({
  name: 'Statistics',
  components: {
    TitleIndex,
    TurnoverStatistics,
    LowStockStatistics,
    OrderStatistics,
    Top,
  },
})
export default class extends Vue {
  private overviewData = {} as any
  private flag = 2
  private tateData = [] as any[]
  private turnoverData = {} as any
  private alertData = {} as any
  private orderData = {
    data: {},
  } as any
  private top10Data = {} as any

  created() {
    this.getTitleNum(2)
  }

  init(begin: any, end: any) {
    this.$nextTick(() => {
      this.getTurnoverStatisticsData(begin, end)
      this.getOrderStatisticsData(begin, end)
      this.getTopData(begin, end)
      this.getAlertData()
    })
  }

  // 营业额统计（新后端直接返回 List，不需要 split）
  async getTurnoverStatisticsData(begin: any, end: any) {
    try {
      const res = await getTurnoverStatistics({ begin, end })
      const d = res.data.data
      this.turnoverData = {
        dateList: d.dateList || [],
        turnoverList: d.turnoverList || [],
      }
    } catch (e) {
      console.error('营业额统计加载失败', e)
    }
  }

  // 库存预警统计
  async getAlertData() {
    try {
      const res = await getStockAlerts({ page: 1, pageSize: 10, alertStatus: 0 })
      if (res.data.code === 1) {
        this.alertData = res.data.data || {}
      }
    } catch (e) {
      console.error('库存预警数据加载失败', e)
    }
  }

  // 订单统计（新后端直接返回 List，不需要 split）
  async getOrderStatisticsData(begin: any, end: any) {
    try {
      const res = await getOrderStatistics({ begin, end })
      const d = res.data.data
      this.orderData = {
        data: {
          dateList: d.dateList || [],
          orderCountList: d.orderCountList || [],
          validOrderCountList: d.validOrderCountList || [],
        },
        totalOrderCount: d.totalOrderCount,
        validOrderCount: d.validOrderCount,
        orderCompletionRate: d.completionRate || 0,
      }
    } catch (e) {
      console.error('订单统计加载失败', e)
    }
  }

  // 销量排行（新后端字段：salesList 替代旧 numberList）
  async getTopData(begin: any, end: any) {
    try {
      const res = await getSalesRanking({ begin, end, limit: 10 })
      const d = res.data.data
      const names = (d.nameList || []).slice().reverse()
      const sales = (d.salesList || []).slice().reverse()
      this.top10Data = {
        nameList: names,
        salesList: sales,
      }
    } catch (e) {
      console.error('销量排行加载失败', e)
    }
  }

  // 标题Tab切换
  getTitleNum(data: number) {
    switch (data) {
      case 1:
        this.tateData = get1stAndToday()
        break
      case 2:
        this.tateData = past7Day()
        break
      case 3:
        this.tateData = past30Day()
        break
      case 4:
        this.tateData = pastWeek()
        break
      case 5:
        this.tateData = pastMonth()
        break
    }
    this.init(this.tateData[0], this.tateData[1])
  }
}
</script>

<style lang="scss">
</style>
