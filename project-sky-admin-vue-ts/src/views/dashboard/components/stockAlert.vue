<template>
  <div class="container">
    <h2 class="homeTitle">
      库存预警<span><router-link to="stock">库存管理</router-link></span>
    </h2>
    <div class="orderviewBox">
      <ul>
        <li>
          <span class="status"><i class="iconfont icon-stop"></i>预警商品数</span>
          <span class="num">{{ stockData.lowStockProductCount || 0 }}</span>
        </li>
        <li>
          <span class="status"><i class="iconfont icon-open"></i>在售总数</span>
          <span class="num">{{ stockData.onSaleProductCount || 0 }}</span>
        </li>
        <li class="add">
          <router-link to="/stock">
            <i></i>
            <p>处理预警</p>
          </router-link>
        </li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
@Component({
  name: 'StockAlert',
})
export default class extends Vue {
  @Prop() private stockData!: any
}
</script>
