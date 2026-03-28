<template>
  <div class="container">
    <h2 class="homeTitle">库存预警统计</h2>
    <div class="charBox">
      <div v-if="!hasData" class="no-data">暂无预警数据</div>
      <div v-else id="alertmain" style="width: 100%; height: 320px"></div>
      <ul class="orderListLine">
        <li class="one"><span style="background:#F56C6C"></span>当前库存</li>
        <li class="three"><span style="background:#FFD000"></span>预警阈值</li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop, Watch } from 'vue-property-decorator'
import * as echarts from 'echarts'
@Component({
  name: 'LowStockStatistics',
})
export default class extends Vue {
  @Prop() private alertdata!: any

  @Watch('alertdata')
  onDataChange() {
    this.$nextTick(() => {
      if (this.hasData) {
        this.initChart()
      }
    })
  }

  get hasData() {
    const records = this.alertdata && this.alertdata.records
    return records && records.length > 0
  }

  initChart() {
    const records = this.alertdata.records || []
    const names = records.map((item: any) => item.productName || '')
    const stocks = records.map((item: any) => item.currentStock || 0)
    const thresholds = records.map((item: any) => item.alertThreshold || 0)

    const chartDom = document.getElementById('alertmain') as any
    if (!chartDom) return
    const myChart = echarts.init(chartDom)
    const option: any = {
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#fff',
        borderRadius: 2,
        textStyle: { color: '#333', fontSize: 12, fontWeight: 300 },
      },
      grid: {
        top: '5%',
        left: '10',
        right: '50',
        bottom: '12%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        axisLabel: {
          textStyle: { color: '#666', fontSize: '12px' },
          rotate: 30,
        },
        data: names,
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLabel: { textStyle: { color: '#666', fontSize: '12px' } },
      },
      series: [
        {
          name: '当前库存',
          type: 'bar',
          barWidth: 16,
          itemStyle: { color: '#F56C6C' },
          data: stocks,
        },
        {
          name: '预警阈值',
          type: 'bar',
          barWidth: 16,
          itemStyle: { color: '#FFD000' },
          data: thresholds,
        },
      ],
    }
    option && myChart.setOption(option)
  }
}
</script>
