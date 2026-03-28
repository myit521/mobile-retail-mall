<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar" style="display: inline-block; width: 100%">
        <label style="margin-right: 10px">分类名称：</label>
        <el-input v-model="name"
                  placeholder="请填写分类名称"
                  style="width: 15%"
                  clearable
                  @clear="init"
                  @keyup.enter.native="init" />

        <div style="float: right">
          <el-button type="primary" class="continue" @click="addClass">
            + 新增分类
          </el-button>
        </div>

        <el-button class="normal-btn continue" @click="init(true)">
          查询
        </el-button>
      </div>
      <el-table v-if="tableData.length" :data="tableData" stripe class="tableBox">
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="sort" label="排序" />
        <el-table-column label="状态">
          <template slot-scope="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': String(scope.row.status) === '0' }">
              {{ String(scope.row.status) === '0' ? '禁用' : '启用' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="操作时间" />
        <el-table-column label="操作" width="200" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="small" class="blueBug"
                       @click="editHandle(scope.row)">修改</el-button>
            <el-button v-if="canDelete" type="text" size="small" class="delBut"
                       @click="deleteHandle(scope.row.id)">删除</el-button>
            <el-button type="text" size="small" class="non"
                       :class="{
                         blueBug: scope.row.status == '0',
                         delBut: scope.row.status != '0'
                       }"
                       @click="statusHandle(scope.row)">
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
    <el-dialog :title="classData.title"
               :visible.sync="classData.dialogVisible"
               width="30%"
               :before-close="handleClose">
      <el-form ref="classData"
               :model="classData"
               class="demo-form-inline"
               :rules="rules"
               label-width="100px">
        <el-form-item label="分类名称：" prop="name">
          <el-input v-model="classData.name" placeholder="请输入分类名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="排序：" prop="sort">
          <el-input v-model="classData.sort" placeholder="请输入排序" />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button size="medium"
                   @click="(classData.dialogVisible = false), $refs.classData.resetFields()">
          取 消
        </el-button>
        <el-button type="primary"
                   :class="{ continue: actionType === 'add' }"
                   size="medium"
                   @click="submitForm()">
          确 定
        </el-button>
        <el-button v-if="action != 'edit'"
                   type="primary" size="medium"
                   @click="submitForm('go')">
          保存并继续添加
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  getCategoryPage,
  deleCategory,
  editCategory,
  addCategory,
  enableOrDisableCategory
} from '@/api/category'
import Empty from '@/components/Empty/index.vue'
import { UserModule } from '@/store/modules/user'

@Component({
  name: 'Category',
  components: { Empty }
})
export default class extends Vue {
  private actionType: string = ''
  private id = ''
  private status = ''
  private name: string = ''
  private action: string = ''
  private counts: number = 0
  private page: number = 1
  private pageSize: number = 10
  private tableData: any[] = []
  private isSearch: boolean = false
  private classData: any = {
    title: '添加分类',
    dialogVisible: false,
    id: '',
    name: '',
    sort: ''
  }

  get canDelete() {
    return UserModule.roles.includes('ADMIN')
  }

  get rules() {
    return {
      name: [
        {
          required: true,
          trigger: 'blur',
          validator: (rule: any, value: string, callback: Function) => {
            var reg = new RegExp('^[A-Za-z\u4e00-\u9fa5]+$')
            if (!value) {
              callback(new Error('分类名称不能为空'))
            } else if (value.length < 2) {
              callback(new Error('分类名称输入不符，请输入2-20个字符'))
            } else if (!reg.test(value)) {
              callback(new Error('分类名称包含特殊字符'))
            } else {
              callback()
            }
          }
        }
      ],
      sort: [
        {
          required: true,
          trigger: 'blur',
          validator: (rule: any, value: string, callback: Function) => {
            if (value || String(value) === '0') {
              const reg = /^\d+$/
              if (!reg.test(value)) {
                callback(new Error('排序只能输入数字类型'))
              } else if (Number(value) > 99) {
                callback(new Error('排序只能输入0-99数字'))
              } else {
                callback()
              }
            } else {
              callback(new Error('排序不能为空'))
            }
          }
        }
      ]
    }
  }

  $refs!: { classData: any }

  created() {
    this.init()
  }

  private async init(isSearch?: any) {
    this.isSearch = isSearch
    await getCategoryPage({
      page: this.page,
      pageSize: this.pageSize,
      name: this.name || undefined
    })
      .then((res: any) => {
        if (String(res.data.code) === '1') {
          this.tableData = res.data.data.records || []
          this.counts = Number(res.data.data.total)
        } else {
          this.$message.error(res.data.msg)
        }
      })
      .catch((err: any) => {
        this.$message.error('请求出错了：' + err.message)
      })
  }

  private addClass() {
    this.classData.title = '新增分类'
    this.action = 'add'
    this.classData.name = ''
    this.classData.sort = ''
    this.classData.id = ''
    this.classData.dialogVisible = true
    this.actionType = 'add'
  }

  private editHandle(dat: any) {
    this.classData.title = '修改分类'
    this.action = 'edit'
    this.classData.name = dat.name
    this.classData.sort = dat.sort
    this.classData.id = dat.id
    this.classData.dialogVisible = true
    this.actionType = 'edit'
  }

  private handleClose() {
    this.classData.dialogVisible = false
    this.$refs.classData.resetFields()
  }

  private statusHandle(row: any) {
    this.id = row.id
    this.status = row.status
    this.$confirm('确认调整该分类的状态?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      enableOrDisableCategory({ id: this.id, status: !this.status ? 1 : 0 })
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success('分类状态更改成功！')
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

  private deleteHandle(id: any) {
    this.$confirm('此操作将永久删除该分类，是否继续？', '确定删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      deleCategory(id)
        .then((res: any) => {
          if (res.data.code === 1) {
            this.$message.success('删除成功！')
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

  submitForm(st?: any) {
    if (this.action === 'add') {
      this.$refs.classData.validate((value: boolean) => {
        if (value) {
          addCategory({
            name: this.classData.name,
            sort: this.classData.sort
          })
            .then((res: any) => {
              if (res.data.code === 1) {
                this.$message.success('分类添加成功！')
                this.$refs.classData.resetFields()
                if (!st) {
                  this.classData.dialogVisible = false
                }
                this.init()
              } else {
                this.$message.error(res.data.msg)
              }
            })
            .catch((err: any) => {
              this.$message.error('请求出错了：' + err.message)
            })
        }
      })
    } else {
      this.$refs.classData.validate((value: boolean) => {
        if (value) {
          editCategory({
            id: this.classData.id,
            name: this.classData.name,
            sort: this.classData.sort
          })
            .then((res: any) => {
              if (res.data.code === 1) {
                this.$message.success('分类修改成功！')
                this.classData.dialogVisible = false
                this.$refs.classData.resetFields()
                this.init()
              } else {
                this.$message.error(res.data.msg)
              }
            })
            .catch((err: any) => {
              this.$message.error('请求出错了：' + err.message)
            })
        }
      })
    }
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
      .tableBar {
        display: flex;
        margin-bottom: 20px;
        justify-content: space-between;
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
      .normal-btn {
        background: #333333;
        color: white;
        margin-left: 20px;
      }
    }
  }
}
</style>
