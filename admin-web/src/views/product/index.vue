<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">商品名称�?/label>
        <el-input v-model="input"
                  placeholder="请填写商品名�?
                  style="width: 14%"
                  clearable
                  @clear="init"
                  @keyup.enter.native="initFun" />

        <label style="margin-right: 10px; margin-left: 20px">商品分类�?/label>
        <el-select v-model="categoryId"
                   style="width: 14%"
                   placeholder="请选择"
                   clearable
                   @clear="init">
          <el-option v-for="item in productCategoryList"
                     :key="item.value"
                     :label="item.label"
                     :value="item.value" />
        </el-select>

        <label style="margin-right: 10px; margin-left: 20px">售卖状态：</label>
        <el-select v-model="productStatus"
                   style="width: 14%"
                   placeholder="请选择"
                   clearable
                   @clear="init">
          <el-option v-for="item in saleStatus"
                     :key="item.value"
                     :label="item.label"
                     :value="item.value" />
        </el-select>

        <label style="margin-right: 10px; margin-left: 20px">手机型号�?/label>
        <el-select v-model="phoneModelId"
                   style="width: 16%"
                   placeholder="请选择"
                   clearable
                   filterable
                   @clear="init">
          <el-option v-for="item in phoneModelOptions"
                     :key="item.id"
                     :label="`${item.brand} ${item.modelName}`"
                     :value="item.id" />
        </el-select>

        <el-button class="normal-btn continue"
                   @click="initFun">
          查询
        </el-button>

        <div class="tableLab">
          <span v-if="canDelete" class="delBut non"
                @click="deleteHandle('批量', null)">批量删除</span>
          <el-button type="primary"
                     style="margin-left: 15px"
                     @click="addProductType('add')">
            + 新建商品
          </el-button>
        </div>
      </div>
      <el-table v-if="tableData.length"
                :data="tableData"
                stripe
                class="tableBox"
                @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="25" />
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="image" label="图片">
          <template slot-scope="{ row }">
            <el-image style="width: 80px; height: 40px; border: none; cursor: pointer"
                      :src="row.image">
              <div slot="error" class="image-slot">
                <img src="./../../assets/noImg.png"
                     style="width: auto; height: 40px; border: none">
              </div>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="商品分类" />
        <el-table-column label="售价">
          <template slot-scope="scope">
            <span style="margin-right: 10px">￥{{ Number(scope.row.price).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存">
          <template slot-scope="scope">
            <span :style="{ color: isLowStock(scope.row) ? '#f56c6c' : '' }">
              {{ scope.row.stock || 0 }}
              <i v-if="isLowStock(scope.row)" class="el-icon-warning" style="color:#f56c6c" title="库存预警" />
            </span>
          </template>
        </el-table-column>
        <el-table-column label="售卖状�?>
          <template slot-scope="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': String(scope.row.status) === '0' }">
              {{ String(scope.row.status) === '0' ? '停售' : '启售' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="最后操作时�? />
        <el-table-column label="操作" width="250" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="small" class="blueBug"
                       @click="addProductType(scope.row.id)">
              修改
            </el-button>
            <el-button v-if="canDelete" type="text" size="small" class="delBut"
                       @click="deleteHandle('单删', scope.row.id)">
              删除
            </el-button>
            <el-button type="text" size="small" class="non"
                       :class="{
                         blueBug: scope.row.status == '0',
                         delBut: scope.row.status != '0'
                       }"
                       @click="statusHandle(scope.row)">
              {{ scope.row.status == '0' ? '启售' : '停售' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else :is-search="isSearch" />
      <el-pagination v-if="counts > 10"
                     class="pageList"
                     :page-sizes="[10, 20, 30, 40]"
                     :page-size="pageSize"
                     layout="total, sizes, prev, pager, next, jumper"
                     :total="counts"
                     @size-change="handleSizeChange"
                     @current-change="handleCurrentChange" />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { UserModule } from '@/store/modules/user'
import { isAdminRole } from '@/utils/role'
import {
  getProductPage,
  deleteProduct,
  productStatusByStatus,
  getCategoryListForProduct
} from '@/api/product'
import { getPhoneModelList } from '@/api/phoneModel'
import Empty from '@/components/Empty/index.vue'

@Component({
  name: 'ProductManage',
  components: { Empty }
})
export default class extends Vue {
  private input: any = ''
  private counts: number = 0
  private page: number = 1
  private pageSize: number = 10
  private checkList: string[] = []
  private tableData: any[] = []
  private productCategoryList: any[] = []
  private phoneModelOptions: any[] = []
  private categoryId = ''
  private productStatus = ''
  private phoneModelId: any = ''
  private isSearch: boolean = false
  private saleStatus: any[] = [
    { value: 0, label: '停售' },
    { value: 1, label: '启售' }
  ]

  get canDelete() {
    return isAdminRole(UserModule.roles)
  }

  created() {
    this.init()
    this.loadCategoryList()
    this.loadPhoneModelList()
  }

  initFun() {
    this.page = 1
    this.init()
  }

  private async init(isSearch?: any) {
    if (isSearch !== undefined) this.isSearch = isSearch
    try {
      const res = await getProductPage({
        page: this.page,
        pageSize: this.pageSize,
        name: this.input || undefined,
        categoryId: this.categoryId || undefined,
        status: this.productStatus !== '' ? this.productStatus : undefined,
        phoneModelId: this.phoneModelId || undefined
      })
      if (res.data.code === 1) {
        this.tableData = res.data.data.records || []
        this.counts = Number(res.data.data.total)
      }
    } catch (err) {
      this.$message.error('请求出错了：' + err.message)
    }
  }

  private isLowStock(row: any): boolean {
    if (row.alertThreshold == null) return false
    return Number(row.stock) <= Number(row.alertThreshold)
  }

  private addProductType(st: string) {
    if (st === 'add') {
      this.$router.push({ path: '/product/add' })
    } else {
      this.$router.push({ path: '/product/add', query: { id: String(st) } })
    }
  }

  private deleteHandle(type: string, id: any) {
    if (type === '批量' && id === null) {
      if (this.checkList.length === 0) {
        return this.$message.error('请选择删除对象')
      }
    }
    this.$confirm('确认删除该商�? 是否继续?', '确定删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      deleteProduct(type === '批量' ? this.checkList.join(',') : id)
        .then((res: any) => {
          if (res && res.data && res.data.code === 1) {
            this.$message.success('删除成功�?)
            this.init()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => {
          this.$message.error('请求出错了：' + err.message)
        })
    })
  }

  private loadCategoryList() {
    getCategoryListForProduct()
      .then((res: any) => {
        if (res && res.data && res.data.code === 1) {
          this.productCategoryList = (res.data.data || []).map((item: any) => ({
            value: item.id,
            label: item.name
          }))
        }
      })
      .catch(() => {})
  }

  private loadPhoneModelList() {
    getPhoneModelList()
      .then((res: any) => {
        if (res && res.data && res.data.code === 1) {
          this.phoneModelOptions = res.data.data || []
        }
      })
      .catch(() => {})
  }

  private statusHandle(row: any) {
    const params: any = {
      id: row.id,
      status: row.status ? 0 : 1
    }
    this.$confirm('确认更改该商品状�?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      productStatusByStatus(params)
        .then((res: any) => {
          if (res && res.data && res.data.code === 1) {
            this.$message.success('商品状态已更改成功�?)
            this.init()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => {
          this.$message.error('请求出错了：' + err.message)
        })
    })
  }

  private handleSelectionChange(val: any) {
    this.checkList = val.map((n: any) => n.id)
  }

  private handleSizeChange(val: any) {
    this.pageSize = val
    this.init()
  }

  private handleCurrentChange(val: any) {
    this.page = val
    this.init()
  }
}
</script>
<style lang="scss">
.el-table-column--selection .cell {
  padding-left: 10px;
}
</style>
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
      .normal-btn {
        background: #333333;
        color: white;
        margin-left: 20px;
      }
      .tableBar {
        margin-bottom: 20px;
        .tableLab {
          display: inline-block;
          float: right;
          span {
            cursor: pointer;
            display: inline-block;
            font-size: 14px;
            padding: 0 20px;
            color: $gray-2;
          }
        }
      }
      .tableBox {
        width: 100%;
        border: 1px solid $gray-5;
        border-bottom: 0;
      }
      .pageList {
        text-align: center;
        margin-top: 30px;
      }
    }
  }
}
</style>



