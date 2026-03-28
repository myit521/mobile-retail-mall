<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">型号名称：</label>
        <el-input v-model="searchName"
                  placeholder="请填写型号名称"
                  style="width: 15%"
                  clearable
                  @clear="init"
                  @keyup.enter.native="initFun" />

        <el-button class="normal-btn continue" style="margin-left: 15px" @click="initFun">查询</el-button>

        <div class="tableLab">
          <el-button type="primary" @click="openAdd">+ 新增型号</el-button>
        </div>
      </div>

      <el-table v-if="tableData.length" :data="tableData" stripe class="tableBox">
        <el-table-column prop="modelName" label="型号名称" />
        <el-table-column prop="brand" label="品牌" />
        <el-table-column prop="series" label="系列" />
        <el-table-column prop="releaseYear" label="发布年份" />
        <el-table-column label="状态">
          <template slot-scope="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': String(scope.row.status) === '0' }">
              {{ String(scope.row.status) === '0' ? '禁用' : '启用' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="small" class="blueBug" @click="openEdit(scope.row)">修改</el-button>
            <el-button v-if="canDelete" type="text" size="small" class="delBut" @click="handleDelete(scope.row.id)">删除</el-button>
            <el-button type="text" size="small" class="non"
                       :class="{
                         blueBug: scope.row.status == '0',
                         delBut: scope.row.status != '0'
                       }"
                       @click="handleStatus(scope.row)">
              {{ scope.row.status == '1' ? '禁用' : '启用' }}
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="40%" :before-close="handleClose">
      <el-form ref="modelForm" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="型号名称：" prop="modelName">
          <el-input v-model="formData.modelName" placeholder="请输入型号名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="品牌：" prop="brand">
          <el-input v-model="formData.brand" placeholder="请输入品牌" maxlength="50" />
        </el-form-item>
        <el-form-item label="系列：">
          <el-input v-model="formData.series" placeholder="请输入系列（可选）" maxlength="50" />
        </el-form-item>
        <el-form-item label="发布年份：">
          <el-input v-model="formData.releaseYear" placeholder="如：2023" maxlength="4" />
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
  getPhoneModelPage,
  addPhoneModel,
  editPhoneModel,
  deletePhoneModel,
  enableOrDisablePhoneModel
} from '@/api/phoneModel'
import Empty from '@/components/Empty/index.vue'
import { UserModule } from '@/store/modules/user'

@Component({
  name: 'PhoneModel',
  components: { Empty }
})
export default class extends Vue {
  private searchName = ''
  private counts = 0
  private page = 1
  private pageSize = 10
  private tableData: any[] = []
  private isSearch = false
  private dialogVisible = false
  private dialogTitle = '新增型号'
  private action = 'add'

  private formData: any = {
    id: '',
    modelName: '',
    brand: '',
    series: '',
    releaseYear: '',
    remark: ''
  }

  get canDelete() {
    return UserModule.roles.includes('ADMIN')
  }

  get rules() {
    return {
      modelName: [{ required: true, message: '请输入型号名称', trigger: 'blur' }],
      brand: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }]
    }
  }

  $refs!: { modelForm: any }

  created() {
    this.init()
  }

  initFun() {
    this.page = 1
    this.init()
  }

  private async init(isSearch?: any) {
    if (isSearch !== undefined) this.isSearch = isSearch
    try {
      const res: any = await getPhoneModelPage({
        page: this.page,
        pageSize: this.pageSize,
        modelName: this.searchName || undefined
      })
      if (res.data.code === 1) {
        this.tableData = res.data.data.records || []
        this.counts = Number(res.data.data.total)
      } else {
        this.$message.error(res.data.msg)
      }
    } catch (err) {
      this.$message.error('请求出错：' + (err as any).message)
    }
  }

  private openAdd() {
    this.action = 'add'
    this.dialogTitle = '新增手机型号'
    this.formData = { id: '', modelName: '', brand: '', series: '', releaseYear: '', remark: '' }
    this.dialogVisible = true
  }

  private openEdit(row: any) {
    this.action = 'edit'
    this.dialogTitle = '修改手机型号'
    this.formData = { ...row }
    this.dialogVisible = true
  }

  private handleClose() {
    this.dialogVisible = false
    this.$refs.modelForm && this.$refs.modelForm.resetFields()
  }

  private handleDelete(id: number) {
    this.$confirm('确认删除该手机型号?', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      deletePhoneModel([id])
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

  private handleStatus(row: any) {
    this.$confirm('确认更改该型号状态?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      enableOrDisablePhoneModel({ id: row.id, status: row.status ? 0 : 1 })
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success('状态更改成功！')
            this.init()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => this.$message.error('请求出错：' + err.message))
    })
  }

  private submitForm() {
    this.$refs.modelForm.validate((valid: boolean) => {
      if (!valid) return
      const api = this.action === 'add' ? addPhoneModel : editPhoneModel
      const params = { ...this.formData }
      if (this.action === 'add') delete params.id
      api(params)
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success(this.action === 'add' ? '添加成功！' : '修改成功！')
            this.handleClose()
            this.init()
          } else {
            this.$message.error(res.data.msg)
          }
        })
        .catch((err: any) => this.$message.error('请求出错：' + err.message))
    })
  }

  private handleSizeChange(val: any) { this.pageSize = val; this.init() }
  private handleCurrentChange(val: any) { this.page = val; this.init() }
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
      .pageList { text-align: center; margin-top: 30px; }
    }
  }
}
</style>
