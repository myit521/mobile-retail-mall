<template>
  <div class="dashboard-container">
    <div class="container">
      <!-- 顶部统计卡片 -->
      <div class="stats-bar">
        <div class="stat-card">
          <span class="stat-num">{{ stats.totalCount || 0 }}</span>
          <span class="stat-label">全部</span>
        </div>
        <div class="stat-card pending">
          <span class="stat-num">{{ stats.pendingCount || 0 }}</span>
          <span class="stat-label">待处理</span>
        </div>
        <div class="stat-card processing">
          <span class="stat-num">{{ stats.processingCount || 0 }}</span>
          <span class="stat-label">处理中</span>
        </div>
        <div class="stat-card completed">
          <span class="stat-num">{{ stats.completedCount || 0 }}</span>
          <span class="stat-label">已完成</span>
        </div>
      </div>

      <!-- 查询栏 -->
      <div class="tableBar">
        <el-input v-model="keyword"
                  placeholder="搜索备忘录内容"
                  style="width: 200px"
                  clearable
                  @clear="initFun"
                  @keyup.enter.native="initFun" />

        <el-select v-model="queryStatus"
                   placeholder="状态"
                   clearable
                   style="width: 110px; margin-left: 12px"
                   @clear="initFun">
          <el-option label="待处理" :value="0" />
          <el-option label="处理中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已取消" :value="3" />
        </el-select>

        <el-select v-model="queryPriority"
                   placeholder="优先级"
                   clearable
                   style="width: 110px; margin-left: 12px"
                   @clear="initFun">
          <el-option label="普通" :value="0" />
          <el-option label="重要" :value="1" />
          <el-option label="紧急" :value="2" />
        </el-select>

        <el-button class="normal-btn continue" style="margin-left: 12px" @click="initFun">查询</el-button>

        <div class="tableLab">
          <el-button type="primary" @click="openAdd">+ 新建备忘录</el-button>
        </div>
      </div>

      <!-- 列表 -->
      <el-table :data="tableData" stripe class="tableBox">
        <el-table-column prop="title" label="标题" min-width="160">
          <template slot-scope="{ row }">
            <span>{{ row.title || row.content.slice(0, 30) }}</span>
            <el-tag v-if="row.isOverdue" type="danger" size="mini" style="margin-left:6px">已逾期</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="优先级" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="priorityType(row.priority)" size="mini">{{ row.priorityDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="statusType(row.status)" size="mini">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="截止日期" width="120" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
        <el-table-column label="操作" width="200" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" size="small" class="blueBug" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0 || row.status === 1"
                       type="text" size="small" class="blueBug"
                       @click="handleComplete(row)">完成</el-button>
            <el-button v-if="row.status !== 2 && row.status !== 3"
                       type="text" size="small"
                       @click="handleCancel(row)">取消</el-button>
            <el-button v-if="canDelete" type="text" size="small" class="delBut" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!tableData.length" style="text-align:center;padding:40px;color:#999">暂无备忘录</div>

      <el-pagination v-if="total > pageSize"
                     class="pageList"
                     :page-sizes="[10, 20, 30]"
                     :page-size="pageSize"
                     layout="total, sizes, prev, pager, next, jumper"
                     :total="total"
                     @size-change="handleSizeChange"
                     @current-change="handleCurrentChange" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="600px" @close="resetForm">
      <el-form ref="memoForm" :model="memoForm" :rules="formRules" label-width="90px">
        <el-form-item label="内容" prop="content">
          <el-input v-model="memoForm.content"
                    type="textarea"
                    :rows="4"
                    placeholder="请输入备忘录内容，支持 AI 智能解析" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="memoForm.priority">
            <el-radio :label="0">普通</el-radio>
            <el-radio :label="1">重要</el-radio>
            <el-radio :label="2">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="memoForm.dueDate"
                          type="date"
                          value-format="yyyy-MM-dd"
                          placeholder="选择截止日期（可选）"
                          style="width: 100%" />
        </el-form-item>
        <el-form-item label="提醒时间">
          <el-date-picker v-model="memoForm.remindTime"
                          type="datetime"
                          value-format="yyyy-MM-dd HH:mm:ss"
                          placeholder="选择提醒时间（可选）"
                          style="width: 100%" />
        </el-form-item>
        <el-form-item label="AI 解析">
          <el-switch v-model="memoForm.enableAiParse" active-text="开启" inactive-text="关闭" />
          <span style="margin-left:10px;color:#999;font-size:12px">开启后保存时自动提取标题、截止日期等信息</span>
        </el-form-item>

        <!-- AI 预览区 -->
        <div v-if="aiResult" class="ai-preview">
          <div class="ai-preview-title">AI 解析结果预览</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="标题">{{ aiResult.title }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ ['普通','重要','紧急'][aiResult.priority] || '-' }}</el-descriptions-item>
            <el-descriptions-item label="截止日期">{{ aiResult.dueDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关键词">{{ (aiResult.tags || []).join(', ') || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="memoForm.enableAiParse && !memoForm.id"
                   :loading="aiLoading"
                   @click="previewAi">AI 预览</el-button>
        <el-button type="primary" :loading="saveLoading" @click="submitForm">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  getMemoPage,
  addMemo,
  editMemo,
  deleteMemo,
  completeMemo,
  cancelMemo,
  getMemoStats,
  parseMemoContent
} from '@/api/memo'
import { UserModule } from '@/store/modules/user'

@Component({ name: 'MemoManage' })
export default class extends Vue {
  private keyword = ''
  private queryStatus: any = ''
  private queryPriority: any = ''
  private page = 1
  private pageSize = 10
  private total = 0
  private tableData: any[] = []
  private stats: any = {}

  private dialogVisible = false
  private dialogTitle = '新建备忘录'
  private saveLoading = false
  private aiLoading = false
  private aiResult: any = null

  private memoForm: any = {
    id: null,
    content: '',
    priority: 0,
    dueDate: '',
    remindTime: '',
    enableAiParse: true
  }

  get canDelete() {
    return UserModule.roles.includes('ADMIN')
  }

  private formRules = {
    content: [{ required: true, message: '请输入备忘录内容', trigger: 'blur' }]
  }

  created() {
    this.init()
    this.loadStats()
  }

  initFun() {
    this.page = 1
    this.init()
  }

  private async init() {
    try {
      const res: any = await getMemoPage({
        page: this.page,
        pageSize: this.pageSize,
        keyword: this.keyword || undefined,
        status: this.queryStatus !== '' ? this.queryStatus : undefined,
        priority: this.queryPriority !== '' ? this.queryPriority : undefined
      })
      if (res.data.code === 1) {
        this.tableData = res.data.data.records || []
        this.total = Number(res.data.data.total)
      }
    } catch (err) {
      this.$message.error('加载失败：' + (err as any).message)
    }
  }

  private loadStats() {
    getMemoStats()
      .then((res: any) => {
        if (res.data.code === 1) this.stats = res.data.data || {}
      })
      .catch(() => {})
  }

  private openAdd() {
    this.dialogTitle = '新建备忘录'
    this.aiResult = null
    this.memoForm = { id: null, content: '', priority: 0, dueDate: '', remindTime: '', enableAiParse: true }
    this.dialogVisible = true
  }

  private openEdit(row: any) {
    this.dialogTitle = '编辑备忘录'
    this.aiResult = null
    this.memoForm = {
      id: row.id,
      content: row.content,
      priority: row.priority,
      dueDate: row.dueDate || '',
      remindTime: row.remindTime || '',
      enableAiParse: false
    }
    this.dialogVisible = true
  }

  private async previewAi() {
    if (!this.memoForm.content) return this.$message.warning('请先输入内容')
    this.aiLoading = true
    this.aiResult = null
    try {
      const res: any = await parseMemoContent(this.memoForm.content)
      if (res.data.code === 1 && res.data.data.success) {
        this.aiResult = res.data.data
      } else {
        this.$message.warning('AI 解析未能提取有效信息')
      }
    } catch (err) {
      this.$message.error('AI 解析失败：' + (err as any).message)
    } finally {
      this.aiLoading = false
    }
  }

  private submitForm() {
    ;(this.$refs.memoForm as any).validate(async (valid: boolean) => {
      if (!valid) return
      this.saveLoading = true
      try {
        const api = this.memoForm.id ? editMemo : addMemo
        const res: any = await api({ ...this.memoForm })
        if (res.data.code === 1) {
          this.$message.success(this.memoForm.id ? '修改成功！' : '创建成功！')
          this.dialogVisible = false
          this.init()
          this.loadStats()
        } else {
          this.$message.error(res.data.msg)
        }
      } catch (err) {
        this.$message.error('请求出错：' + (err as any).message)
      } finally {
        this.saveLoading = false
      }
    })
  }

  private handleComplete(row: any) {
    this.$confirm('确认将该备忘录标记为已完成？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'info'
    }).then(() => {
      completeMemo(row.id).then((res: any) => {
        if (res.data.code === 1) {
          this.$message.success('已完成')
          this.init()
          this.loadStats()
        }
      })
    })
  }

  private handleCancel(row: any) {
    this.$confirm('确认取消该备忘录？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    }).then(() => {
      cancelMemo(row.id).then((res: any) => {
        if (res.data.code === 1) {
          this.$message.success('已取消')
          this.init()
          this.loadStats()
        }
      })
    })
  }

  private handleDelete(row: any) {
    this.$confirm('确认删除该备忘录？', '提示', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    }).then(() => {
      deleteMemo(row.id).then((res: any) => {
        if (res.data.code === 1) {
          this.$message.success('删除成功')
          this.init()
          this.loadStats()
        }
      })
    })
  }

  private resetForm() {
    this.aiResult = null
    const form = this.$refs.memoForm as any
    if (form) form.resetFields()
  }

  private priorityType(p: number) {
    return ['', 'warning', 'danger'][p] || ''
  }

  private statusType(s: number) {
    return ['info', 'primary', 'success', ''][s] || ''
  }

  private handleSizeChange(val: number) {
    this.pageSize = val
    this.init()
  }

  private handleCurrentChange(val: number) {
    this.page = val
    this.init()
  }
}
</script>

<style lang="scss" scoped>
.dashboard-container {
  .container {
    background: #fff;
    position: relative;
    z-index: 1;
    padding: 30px 28px;
    border-radius: 4px;
    margin: 30px;
  }
  .stats-bar {
    display: flex;
    gap: 16px;
    margin-bottom: 24px;
    .stat-card {
      flex: 1;
      background: #f5f7fa;
      border-radius: 6px;
      padding: 16px 20px;
      text-align: center;
      .stat-num {
        display: block;
        font-size: 28px;
        font-weight: bold;
        color: #333;
      }
      .stat-label {
        font-size: 13px;
        color: #666;
      }
      &.pending .stat-num { color: #E6A23C; }
      &.processing .stat-num { color: #409EFF; }
      &.completed .stat-num { color: #67C23A; }
    }
  }
  .tableBar {
    margin-bottom: 20px;
    .tableLab {
      display: inline-block;
      float: right;
    }
    .normal-btn {
      background: #333;
      color: white;
    }
  }
  .tableBox {
    width: 100%;
    border: 1px solid #ebeef5;
    border-bottom: 0;
  }
  .pageList {
    text-align: center;
    margin-top: 30px;
  }
  .ai-preview {
    margin-top: 12px;
    padding: 12px;
    background: #f0f9eb;
    border-radius: 4px;
    border: 1px solid #b3e19d;
    .ai-preview-title {
      font-size: 13px;
      color: #67C23A;
      margin-bottom: 8px;
      font-weight: bold;
    }
  }
}
</style>
