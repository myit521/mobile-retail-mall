<template>
  <div class="container">
    <h2 class="homeTitle">
      今日数据<i>{{ days[1] }}</i
      ><span><router-link to="statistics">详细数据</router-link></span>
    </h2>
    <div class="overviewBox">
      <ul>
        <li>
          <p class="tit">今日营业额</p>
          <p class="num">¥ {{ overviewData.todayTurnover || '0.00' }}</p>
        </li>
        <li>
          <p class="tit">今日订单数</p>
          <p class="num">{{ overviewData.todayOrderCount || 0 }}</p>
        </li>
        <li>
          <p class="tit">今日有效订单</p>
          <p class="num">{{ overviewData.todayValidOrderCount || 0 }}</p>
        </li>
        <li>
          <p class="tit">待处理订单</p>
          <p class="num">{{ overviewData.waitingOrderCount || 0 }}</p>
        </li>
        <li>
          <p class="tit">待处理备忘录</p>
          <p class="num">{{ overviewData.pendingMemoCount || 0 }}</p>
        </li>
      </ul>
    </div>
  </div>
</template>
<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
import { getday } from '@/utils/formValidate'

@Component({
  name: 'Overview',
})
export default class extends Vue {
  @Prop() private overviewData!: any

  get days() {
    return getday()
  }
}
</script>
