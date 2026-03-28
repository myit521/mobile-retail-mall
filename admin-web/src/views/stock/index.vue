<template>
  <div class="dashboard-container">
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <el-tab-pane label="库存列表" name="list" />
      <el-tab-pane label="库存预警" name="alert" />
      <el-tab-pane label="出入库日志" name="log" />
    </el-tabs>

    <!-- 库存列表 -->
    <div v-if="activeTab === 'list'" class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">商品名称：</label>
        <el-input v-model="searchName" placeholder="请填写商品名称"
                  style="width: 15%" clearable @clear="loadProducts" @keyup.enter.native="loadProducts" />
        <el-button class="normal-btn continue" style="margin-left: 15px" @click="loadProducts">查询</el-button>
      </div>
      <el-table v-if="productList.length" :data="productList" stripe class="tableBox">
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="categoryName" label="分类" />
        <el-table-column label="当前库存">
          <template slot-scope="scope">
            <span :style="{ color: isLowStock(scope.row) ? '#f56c6c' : '' }">
              {{ scope.row.stock || 0 }}
              <i v-if="isLowStock(scope.row)" class="el-icon-warning" style="color:#f56c6c" title="库存预警" />
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="alertThreshold" label="预警阈值" />
        <el-table-column label="操作" align="center" width="160">
          <template slot-scope="scope">
            <el-button type="text" class="blueBug" @click="openAdjust(scope.row)">调整库存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else :is-search="!!searchName" />
      <el-pagination v-if="productCounts > 10" class="pageList"
                     :page-sizes="[10, 20, 30, 40]" :page-size="productPageSize"
                     layout="total, sizes, prev, pager, next, jumper" :total="productCounts"
                     @size-change="(v) => { productPageSize = v; loadProducts() }"
                     @current-change="(v) => { productPage = v; loadProducts() }" />
    </div>

    <!-- 库存预警 -->
    <div v-if="activeTab === 'alert'" class="container">
      <div class="tableBar">
        <el-select v-model="alertStatus" placeholder="处理状态" clearable style="width: 140px" @change="loadAlerts">
          <el-option label="未处理" :value="0" />
          <el-option label="已处理" :value="1" />
          <el-option label="已忽略" :value="2" />
        </el-select>
        <el-button class="normal-btn continue" style="margin-left: 15px" @click="loadAlerts">查询</el-button>
      </div>
      <el-table v-if="alertList.length" :data="alertList" stripe class="tableBox">
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column prop="currentStock" label="当前库存" />
        <el-table-column prop="alertThreshold" label="预警阈值" />
        <el-table-column prop="alertTime" label="预警时间" min-width="110" />
        <el-table-column label="状态">
          <template slot-scope="scope">
            <span>{{ ['未处理','已处理','已忽略'][scope.row.alertStatus] || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180">
          <template slot-scope="scope">
            <el-button v-if="scope.row.alertStatus === 0" type="text" class="blueBug"
                       @click="handleAlert(scope.row.id, 1)">标记已处理</el-button>
            <el-button v-if="scope.row.alertStatus === 0" type="text" class="non"
                       @click="handleAlert(scope.row.id, 2)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else :is-search="false" />
      <el-pagination v-if="alertCounts > 10" class="pageList"
                     :page-sizes="[10, 20, 30, 40]" :page-size="alertPageSize"
                     layout="total, sizes, prev, pager, next, jumper" :total="alertCounts"
                     @size-change="(v) => { alertPageSize = v; loadAlerts() }"
                     @current-change="(v) => { alertPage = v; loadAlerts() }" />
    </div>

    <!-- 出入库日志 -->
    <div v-if="activeTab === 'log'" class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">商品名称：</label>
        <el-input v-model="logSearchName" placeholder="请填写商品名称"
                  style="width: 15%" clearable @clear="loadLogs" @keyup.enter.native="loadLogs" />
        <el-button class="normal-btn continue" style="margin-left: 15px" @click="loadLogs">查询</el-button>
      </div>
      <el-table v-if="logList.length" :data="logList" stripe class="tableBox">
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column label="变动类型">
          <template slot-scope="scope">
            <span :style="{ color: scope.row.changeType === 1 ? '#67c23a' : '#f56c6c' }">
              {{ scope.row.changeType === 1 ? '入库' : scope.row.changeType === 2 ? '出库' : '手动调整' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动数量">
          <template slot-scope="scope">
            <span :style="{ color: scope.row.changeQuantity >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ scope.row.changeQuantity >= 0 ? '+' : '' }}{{ scope.row.changeQuantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="beforeStock" label="变动前库存" />
        <el-table-column prop="afterStock" label="变动后库存" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="createTime" label="时间" min-width="110" />
      </el-table>
      <Empty v-else :is-search="false" />
      <el-pagination v-if="logCounts > 10" class="pageList"
                     :page-sizes="[10, 20, 30, 40]" :page-size="logPageSize"
                     layout="total, sizes, prev, pager, next, jumper" :total="logCounts"
                     @size-change="(v) => { logPageSize = v; loadLogs() }"
                     @current-change="(v) => { logPage = v; loadLogs() }" />
    </div>

    <!-- 调整库存弹窗 -->
    <el-dialog title="调整库存" :visible.sync="adjustVisible" width="35%">
      <el-form ref="adjustForm" :model="adjustData" :rules="adjustRules" label-width="100px">
        <el-form-item label="商品名称：">
          <span>{{ adjustData.productName }}</span>
        </el-form-item>
        <el-form-item label="当前库存：">
          <span>{{ adjustData.currentStock }}</span>
        </el-form-item>
        <el-form-item label="调整数量：" prop="changeQuantity">
          <el-input-number v-model="adjustData.changeQuantity" placeholder="正数为入库，负数为出库" />
          <span style="margin-left: 8px; color: #999;">正数入库，负数出库</span>
        </el-form-item>
        <el-form-item label="备注：">
          <el-input v-model="adjustData.remark" type="textarea" :rows="2" placeholder="备注信息（可选）" maxlength="200" />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="adjustVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitAdjust">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { getProductPage } from '@/api/product'
import { adjustStock, getStockLogs, getStockAlerts, handleStockAlert } from '@/api/stock'
import Empty from '@/components/Empty/index.vue'

@Component({
  name: 'StockManage',
  components: { Empty }
})
export default class extends Vue {
  private activeTab = 'list'

  // 库存列表
  private searchName = ''
  private productList: any[] = []
  private productCounts = 0
  private productPage = 1
  private productPageSize = 10

  // 预警
  private alertStatus: any = 0
  private alertList: any[] = []
  private alertCounts = 0
  private alertPage = 1
  private alertPageSize = 10

  // 日志
  private logSearchName = ''
  private logList: any[] = []
  private logCounts = 0
  private logPage = 1
  private logPageSize = 10

  // 调整
  private adjustVisible = false
  private adjustData: any = {
    productId: '',
    productName: '',
    currentStock: 0,
    changeQuantity: 0,
    remark: ''
  }

  get adjustRules() {
    return {
      changeQuantity: [
        { required: true, message: '请输入调整数量', trigger: 'blur' },
        { type: 'number', message: '请输入有效数字', trigger: 'blur' }
      ]
    }
  }

  $refs!: { adjustForm: any }

  created() {
    this.loadProducts()
  }

  handleTabClick() {
    if (this.activeTab === 'list') this.loadProducts()
    else if (this.activeTab === 'alert') this.loadAlerts()
    else if (this.activeTab === 'log') this.loadLogs()
  }

  private isLowStock(row: any) {
    if (row.alertThreshold == null) return false
    return Number(row.stock) <= Number(row.alertThreshold)
  }

  async loadProducts() {
    try {
      const res: any = await getProductPage({
        page: this.productPage,
        pageSize: this.productPageSize,
        name: this.searchName || undefined
      })
      if (res.data.code === 1) {
        this.productList = res.data.data.records || []
        this.productCounts = Number(res.data.data.total)
      }
    } catch (err) {
      this.$message.error('请求出错：' + (err as any).message)
    }
  }

  async loadAlerts() {
    try {
      const res: any = await getStockAlerts({
        page: this.alertPage,
        pageSize: this.alertPageSize,
        alertStatus: this.alertStatus !== '' ? this.alertStatus : undefined
      })
      if (res.data.code === 1) {
        this.alertList = res.data.data.records || []
        this.alertCounts = Number(res.data.data.total)
      }
    } catch (err) {
      this.$message.error('请求出错：' + (err as any).message)
    }
  }

  async loadLogs() {
    try {
      const res: any = await getStockLogs({
        page: this.logPage,
        pageSize: this.logPageSize,
        productName: this.logSearchName || undefined
      })
      if (res.data.code === 1) {
        this.logList = res.data.data.records || []
        this.logCounts = Number(res.data.data.total)
      }
    } catch (err) {
      this.$message.error('请求出错：' + (err as any).message)
    }
  }

  private openAdjust(row: any) {
    this.adjustData = {
      productId: row.id,
      productName: row.name,
      currentStock: row.stock || 0,
      changeQuantity: 0,
      remark: ''
    }
    this.adjustVisible = true
  }

  private submitAdjust() {
    this.$refs.adjustForm.validate((valid: boolean) => {
      if (!valid) return
      adjustStock({
        productId: this.adjustData.productId,
        changeQuantity: this.adjustData.changeQuantity,
        remark: this.adjustData.remark
      })
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success('库存调整成功！')
            this.adjustVisible = false
            this.loadProducts()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => this.$message.error('请求出错：' + err.message))
    })
  }

  private handleAlert(id: number, status: number) {
    handleStockAlert(id, status)
      .then((res: any) => {
        if (res.data.code === 1) {
          this.$message.success('操作成功！')
          this.loadAlerts()
        } else {
          this.$message.error(res.data.msg)
        }
      })
      .catch((err: any) => this.$message.error('请求出错：' + err.message))
  }
}
</script>
<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;
    .container {
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 30px 28px;
      border-radius: 4px;
      .normal-btn { background: #333333; color: white; margin-left: 20px; }
      .tableBar { margin-bottom: 20px; }
      .tableBox { width: 100%; border: 1px solid $gray-5; border-bottom: 0; }
      .pageList { text-align: center; margin-top: 30px; }
    }
  }
}
</style>
