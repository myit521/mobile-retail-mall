<template>
  <div class="container">
    <h2 class="homeTitle">
      商品总览<span><router-link to="product">商品管理</router-link></span>
    </h2>
    <div class="orderviewBox">
      <ul>
        <li>
          <span class="status"><i class="iconfont icon-open"></i>在售商品</span>
          <span class="num">{{ productData.onSaleProductCount || 0 }}</span>
        </li>
        <li>
          <span class="status"><i class="iconfont icon-stop"></i>商品总数</span>
          <span class="num">{{ productData.productCount || 0 }}</span>
        </li>
        <li class="add">
          <router-link to="/product/add">
            <i></i>
            <p>新增商品</p>
          </router-link>
        </li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
@Component({
  name: 'ProductStatistics',
})
export default class extends Vue {
  @Prop() private productData!: any
}
</script>
