<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">货架编号：</label>
        <el-input v-model="searchShelf" placeholder="请填写货架编号"
                  style="width: 15%" clearable @clear="init" @keyup.enter.native="initFun" />
        <el-button class="normal-btn continue" style="margin-left: 15px" @click="initFun">查询</el-button>
        <div class="tableLab">
          <el-button type="primary" @click="openAdd">+ 设置位置</el-button>
        </div>
      </div>

      <el-table v-if="tableData.length" :data="tableData" stripe class="tableBox">
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column prop="shelfCode" label="货架编号" />
        <el-table-column prop="locationCode" label="位置编号" />
        <el-table-column prop="row" label="行" />
        <el-table-column prop="col" label="列" />
        <el-table-column prop="updateTime" label="更新时间" min-width="110" />
        <el-table-column label="操作" align="center" width="160">
          <template slot-scope="scope">
            <el-button type="text" class="blueBug" @click="openEdit(scope.row)">修改</el-button>
            <el-button v-if="canDelete" type="text" class="delBut" @click="handleDelete(scope.row.productId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else :is-search="!!searchShelf" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="40%" :before-close="handleClose">
      <el-form ref="locationForm" :model="formData" :rules="rules" label-width="110px">
        <el-form-item label="商品：" prop="productId">
          <el-select v-model="formData.productId" placeholder="请选择商品" filterable style="width: 100%">
            <el-option v-for="item in productOptions"
                       :key="item.id"
                       :label="item.name"
                       :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="货架编号：" prop="shelfCode">
          <el-input v-model="formData.shelfCode" placeholder="如：A-01" maxlength="20" />
        </el-form-item>
        <el-form-item label="位置编号：">
          <el-input v-model="formData.locationCode" placeholder="如：A-01-03（可选）" maxlength="30" />
        </el-form-item>
        <el-form-item label="行：">
          <el-input-number v-model="formData.row" :min="1" placeholder="行号" />
        </el-form-item>
        <el-form-item label="列：">
          <el-input-number v-model="formData.col" :min="1" placeholder="列号" />
        </el-form-item>
        <el-form-item label="备注：">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息（可选）" maxlength="200" />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  getProductLocationList,
  setProductLocation,
  deleteProductLocation
} from '@/api/productLocation'
import { getProductList } from '@/api/product'
import Empty from '@/components/Empty/index.vue'
import { UserModule } from '@/store/modules/user'

@Component({
  name: 'ProductLocation',
  components: { Empty }
})
export default class extends Vue {
  private searchShelf = ''
  private tableData: any[] = []
  private productOptions: any[] = []
  private dialogVisible = false
  private dialogTitle = '设置商品位置'
  private action = 'add'

  private formData: any = {
    productId: '',
    shelfCode: '',
    locationCode: '',
    row: 1,
    col: 1,
    remark: ''
  }

  get canDelete() {
    return UserModule.roles.includes('ADMIN')
  }

  get rules() {
    return {
      productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
      shelfCode: [{ required: true, message: '请输入货架编号', trigger: 'blur' }]
    }
  }

  $refs!: { locationForm: any }

  created() {
    this.init()
    this.loadProductOptions()
  }

  initFun() {
    this.init()
  }

  private async init() {
    try {
      const res: any = await getProductLocationList(this.searchShelf || undefined)
      if (res.data.code === 1) {
        this.tableData = res.data.data || []
      } else {
        this.$message.error(res.data.msg)
      }
    } catch (err) {
      this.$message.error('请求出错：' + (err as any).message)
    }
  }

  private async loadProductOptions() {
    try {
      const res: any = await getProductList()
      if (res.data.code === 1) {
        this.productOptions = res.data.data || []
      }
    } catch (err) {
      console.error('加载商品列表失败', err)
    }
  }

  private openAdd() {
    this.action = 'add'
    this.dialogTitle = '设置商品位置'
    this.formData = { productId: '', shelfCode: '', locationCode: '', row: 1, col: 1, remark: '' }
    this.dialogVisible = true
  }

  private openEdit(row: any) {
    this.action = 'edit'
    this.dialogTitle = '修改商品位置'
    this.formData = { ...row }
    this.dialogVisible = true
  }

  private handleClose() {
    this.dialogVisible = false
    this.$refs.locationForm && this.$refs.locationForm.resetFields()
  }

  private handleDelete(productId: number) {
    this.$confirm('确认删除该商品位置记录?', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      deleteProductLocation(productId)
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success('删除成功！')
            this.init()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => this.$message.error('请求出错：' + err.message))
    })
  }

  private submitForm() {
    this.$refs.locationForm.validate((valid: boolean) => {
      if (!valid) return
      setProductLocation({ ...this.formData })
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success(this.action === 'add' ? '位置设置成功！' : '修改成功！')
            this.handleClose()
            this.init()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => this.$message.error('请求出错：' + err.message))
    })
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
      .tableBar {
        margin-bottom: 20px;
        .tableLab { display: inline-block; float: right; }
      }
      .tableBox { width: 100%; border: 1px solid $gray-5; border-bottom: 0; }
    }
  }
}
</style>
