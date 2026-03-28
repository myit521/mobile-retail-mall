<template>
  <div :key="vueRest" class="addBrand-container">
    <div :key="restKey" class="container">
      <el-form ref="ruleForm"
               :model="ruleForm"
               :rules="rules"
               :inline="true"
               label-width="180px"
               class="demo-ruleForm">
        <div>
          <el-form-item label="商品名称:" prop="name">
            <el-input v-model="ruleForm.name"
                      placeholder="请填写商品名称"
                      maxlength="20" />
          </el-form-item>
          <el-form-item label="商品分类:" prop="categoryId">
            <el-select v-model="ruleForm.categoryId" placeholder="请选择商品分类">
              <el-option v-for="(item, index) in categoryList"
                         :key="index"
                         :label="item.name"
                         :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="商品价格:" prop="price">
            <el-input v-model="ruleForm.price" placeholder="请设置商品价格" />
          </el-form-item>
          <el-form-item label="库存数量:" prop="stock">
            <el-input-number v-model="ruleForm.stock" :min="0" placeholder="库存数量" />
          </el-form-item>
        </div>
        <div>
          <el-form-item label="预警阈值:">
            <el-input-number v-model="ruleForm.alertThreshold" :min="0" placeholder="低于此值触发预警" />
          </el-form-item>
        </div>

        <!-- 适配手机型号（多选） -->
        <div>
          <el-form-item label="适配手机型号:">
            <el-select v-model="selectedPhoneModelIds"
                       placeholder="请选择适配型号（可多选，可选）"
                       multiple
                       clearable
                       filterable
                       style="width: 500px">
              <el-option v-for="item in phoneModelList"
                         :key="item.id"
                         :label="`${item.brand} ${item.modelName}${item.series ? ' ' + item.series : ''}${item.releaseYear ? ' (' + item.releaseYear + ')' : ''}`"
                         :value="item.id" />
            </el-select>
          </el-form-item>
        </div>

        <!-- 摆放位置 -->
        <div>
          <el-form-item label="货架编号:">
            <el-input v-model="locationForm.shelfCode"
                      placeholder="如：A-01（可选）"
                      style="width: 200px" />
          </el-form-item>
          <el-form-item label="位置编号:">
            <el-input v-model="locationForm.locationCode"
                      placeholder="如：A-01-03（可选）"
                      style="width: 200px" />
          </el-form-item>
        </div>
        <div>
          <el-form-item label="货架行:">
            <el-input-number v-model="locationForm.row" :min="1" />
          </el-form-item>
          <el-form-item label="货架列:">
            <el-input-number v-model="locationForm.col" :min="1" />
          </el-form-item>
        </div>

        <div>
          <el-form-item label="商品图片:" prop="image">
            <image-upload :prop-image-url="imageUrl"
                          @imageChange="imageChange">
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
        <div class="address">
          <el-form-item label="商品描述:" prop="region">
            <el-input v-model="ruleForm.description"
                      type="textarea"
                      :rows="3"
                      maxlength="200"
                      placeholder="商品描述，最长200字" />
          </el-form-item>
        </div>
        <div class="subBox address">
          <el-button @click="() => $router.back()">取消</el-button>
          <el-button type="primary"
                     :class="{ continue: actionType === 'add' }"
                     @click="submitForm('ruleForm', null)">
            保存
          </el-button>
          <el-button v-if="actionType == 'add'"
                     type="primary"
                     @click="submitForm('ruleForm', 'goAnd')">
            保存并继续添加
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import ImageUpload from '@/components/ImgUpload/index.vue'
import {
  queryProductById,
  addProduct,
  editProduct,
  getCategoryListForProduct
} from '@/api/product'
import {
  getProductLocationById,
  setProductLocation
} from '@/api/productLocation'
import { getPhoneModelList } from '@/api/phoneModel'

@Component({
  name: 'AddProduct',
  components: { ImageUpload }
})
export default class extends Vue {
  private restKey: number = 0
  private imageUrl: string = ''
  private actionType: string = ''
  private categoryList: any[] = []
  private phoneModelList: any[] = []
  private selectedPhoneModelIds: number[] = []
  private vueRest = '1'

  private ruleForm: any = {
    name: '',
    id: '',
    price: '',
    image: '',
    description: '',
    status: 1,
    categoryId: '',
    stock: 0,
    alertThreshold: 0
  }

  private locationForm: any = {
    shelfCode: '',
    locationCode: '',
    row: 1,
    col: 1
  }

  get rules() {
    return {
      name: [
        {
          required: true,
          validator: (rule: any, value: string, callback: Function) => {
            if (!value) {
              callback(new Error('请输入商品名称'))
            } else {
              const reg = /^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/
              if (!reg.test(value)) {
                callback(new Error('商品名称输入不符，请输入2-20个字符'))
              } else {
                callback()
              }
            }
          },
          trigger: 'blur'
        }
      ],
      categoryId: [
        { required: true, message: '请选择商品分类', trigger: 'change' }
      ],
      price: [
        {
          required: true,
          validator: (rules: any, value: string, callback: Function) => {
            const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
            if (!reg.test(value) || Number(value) <= 0) {
              callback(new Error('商品价格格式有误，请输入大于零且最多保留两位小数的金额'))
            } else {
              callback()
            }
          },
          trigger: 'blur'
        }
      ]
    }
  }

  created() {
    this.loadCategoryList()
    this.loadPhoneModelList()
    this.actionType = this.$route.query.id ? 'edit' : 'add'
    if (this.$route.query.id) {
      this.loadProduct()
      this.loadLocation(this.$route.query.id)
    }
  }

  private loadCategoryList() {
    getCategoryListForProduct()
      .then((res: any) => {
        if (res.data.code === 1) {
          this.categoryList = res.data.data || []
        }
      })
      .catch(() => {})
  }

  private loadPhoneModelList() {
    getPhoneModelList()
      .then((res: any) => {
        if (res.data.code === 1) {
          this.phoneModelList = res.data.data || []
        }
      })
      .catch(() => {})
  }

  private async loadProduct() {
    const res: any = await queryProductById(this.$route.query.id)
    if (res && res.data && res.data.code === 1) {
      const d = res.data.data
      this.ruleForm = {
        ...d,
        price: String(d.price),
        stock: d.stock || 0,
        alertThreshold: d.alertThreshold || 0
      }
      this.imageUrl = d.image || ''
      // 回填已关联的手机型号 IDs
      if (d.phoneModelList && d.phoneModelList.length > 0) {
        this.selectedPhoneModelIds = d.phoneModelList.map((m: any) => m.id)
      }
    } else {
      this.$message.error(res.data.msg)
    }
  }

  private loadLocation(productId: any) {
    getProductLocationById(productId)
      .then((res: any) => {
        if (res && res.data && res.data.code === 1 && res.data.data) {
          const loc = res.data.data
          this.locationForm = {
            shelfCode: loc.shelfCode || '',
            locationCode: loc.locationCode || '',
            row: loc.row || 1,
            col: loc.col || 1
          }
        }
      })
      .catch(() => {})
  }

  private saveLocation(productId: any) {
    if (!this.locationForm.shelfCode) return
    setProductLocation({
      productId,
      ...this.locationForm
    }).catch(() => {})
  }

  private resetForm() {
    this.imageUrl = ''
    this.selectedPhoneModelIds = []
    this.locationForm = { shelfCode: '', locationCode: '', row: 1, col: 1 }
    this.ruleForm = {
      name: '', id: '', price: '', image: '',
      description: '', status: 1, categoryId: '',
      stock: 0, alertThreshold: 0
    }
    this.restKey++
  }

  private submitForm(formName: any, st: any) {
    ;(this.$refs[formName] as any).validate((valid: any) => {
      if (valid) {
        if (!this.ruleForm.image) return this.$message.error('商品图片不能为空')
        const params: any = { ...this.ruleForm }
        params.price = Number(params.price)
        params.status = this.actionType === 'add' ? 1 : (this.ruleForm.status ? 1 : 0)
        // 将选中的型号 IDs 放入提交参数
        params.phoneModelIds = this.selectedPhoneModelIds || []

        if (this.actionType === 'add') {
          delete params.id
          addProduct(params)
            .then((res: any) => {
              if (res.data.code === 1) {
                const newId = res.data.data
                if (newId) this.saveLocation(newId)
                this.$message.success('商品添加成功！')
                if (!st) {
                  this.$router.push({ path: '/product' })
                } else {
                  this.resetForm()
                }
              } else {
                this.$message.error(res.data.msg)
              }
            })
            .catch((err: any) => {
              this.$message.error('请求出错了：' + err.message)
            })
        } else {
          delete params.createTime
          delete params.updateTime
          editProduct(params)
            .then((res: any) => {
              if (res && res.data && res.data.code === 1) {
                this.saveLocation(this.ruleForm.id)
                this.$router.push({ path: '/product' })
                this.$message.success('商品修改成功！')
              } else {
                this.$message.error(res.data.msg)
              }
            })
            .catch((err: any) => {
              this.$message.error('请求出错了：' + err.message)
            })
        }
      } else {
        return false
      }
    })
  }

  imageChange(value: any) {
    this.ruleForm.image = value
  }
}
</script>
<style lang="scss" scoped>
.addBrand-container {
  .el-form--inline .el-form-item__content {
    width: 293px;
  }
  .el-input {
    width: 350px;
  }
  .address {
    .el-form-item__content {
      width: 777px !important;
    }
  }
}
.addBrand {
  &-container {
    margin: 30px;
    .container {
      position: relative;
      z-index: 1;
      background: #fff;
      padding: 30px;
      border-radius: 4px;
      min-height: 500px;
      .subBox {
        padding-top: 30px;
        text-align: center;
        border-top: solid 1px $gray-5;
      }
    }
  }
}
</style>
