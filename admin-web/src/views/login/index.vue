<template>
  <div class="login">
    <div class="login-box">
      <div class="login-visual">
        <div class="login-visual-content">
          <p class="visual-badge">Admin Console</p>
          <h1>手机商城管理后台</h1>
          <p>统一处理商品、库存、订单和备忘录等核心业务。</p>
        </div>
      </div>
      <div class="login-form">
        <el-form
          ref="loginForm"
          :model="loginForm"
          :rules="loginRules"
          @submit.native.prevent
        >
          <div class="login-form-title">
            <span class="title-label">手机商城管理系统</span>
          </div>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="账号"
              prefix-icon="iconfont icon-user"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="iconfont icon-lock"
              @keyup.enter.native="handleLogin"
            />
          </el-form-item>
          <el-form-item style="width: 100%">
            <el-button
              :loading="loading"
              class="login-btn"
              native-type="button"
              size="medium"
              type="primary"
              style="width: 100%"
              @click.native.prevent="handleLogin"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
type Route = any
import { Form as ElForm } from 'element-ui'
import { UserModule } from '@/store/modules/user'

@Component({
  name: 'Login',
})
export default class extends Vue {
  private validateUsername = (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请输入用户名'))
    } else {
      callback()
    }
  }

  private validatePassword = (rule: any, value: string, callback: Function) => {
    if (value.length < 6) {
      callback(new Error('密码长度不能少于 6 位'))
    } else {
      callback()
    }
  }

  private loginForm = {
    username: '',
    password: '',
  } as {
    username: String
    password: String
  }

  loginRules = {
    username: [{ validator: this.validateUsername, trigger: 'blur' }],
    password: [{ validator: this.validatePassword, trigger: 'blur' }],
  }

  private loading = false
  private redirect?: string

  @Watch('$route', { immediate: true })
  private onRouteChange(route: Route) {
    this.redirect = route.query && route.query.redirect
  }

  private handleLogin() {
    ;(this.$refs.loginForm as ElForm).validate(async (valid: boolean) => {
      if (!valid) {
        return false
      }

      this.loading = true
      await UserModule.Login(this.loginForm as any)
        .then((res: any) => {
          if (String(res.code) === '1') {
            this.$router.push(this.redirect || '/')
          } else {
            this.loading = false
          }
        })
        .catch(() => {
          this.loading = false
        })
    })
  }
}
</script>

<style lang="scss">
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background: linear-gradient(135deg, #0f172a 0%, #1d4ed8 100%);
}

.login-box {
  width: 1000px;
  height: 474px;
  border-radius: 8px;
  display: flex;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.24);
}

.login-visual {
  width: 60%;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.5), transparent 35%),
    radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.35), transparent 30%),
    linear-gradient(135deg, #082f49 0%, #1d4ed8 55%, #2563eb 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.login-visual-content {
  max-width: 360px;

  .visual-badge {
    display: inline-flex;
    padding: 6px 12px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.14);
    font-size: 12px;
    letter-spacing: 1px;
    text-transform: uppercase;
  }

  h1 {
    margin: 18px 0 14px;
    font-size: 34px;
    line-height: 1.2;
    font-weight: 700;
  }

  p {
    margin: 0;
    font-size: 15px;
    line-height: 1.8;
    color: rgba(255, 255, 255, 0.82);
  }
}

.login-form {
  background: #ffffff;
  width: 40%;
  display: flex;
  justify-content: center;
  align-items: center;

  .el-form {
    width: 240px;
  }

  .el-form-item {
    margin-bottom: 30px;
  }

  .el-form-item.is-error .el-input__inner {
    border: 0 !important;
    border-bottom: 1px solid #fd7065 !important;
    background: #fff !important;
  }

  .el-input__inner {
    border: 0;
    border-bottom: 1px solid #e9e9e8;
    border-radius: 0;
    font-size: 12px;
    font-weight: 400;
    color: #333333;
    height: 32px;
    line-height: 32px;
  }

  .el-input__prefix {
    left: 0;
  }

  .el-input--prefix .el-input__inner {
    padding-left: 26px;
  }

  .el-input__inner::placeholder {
    color: #aeb5c4;
  }

  .el-form-item--medium .el-form-item__content {
    line-height: 32px;
  }

  .el-input--medium .el-input__icon {
    line-height: 32px;
  }
}

.login-btn {
  border-radius: 17px;
  padding: 11px 20px !important;
  margin-top: 10px;
  font-weight: 500;
  font-size: 12px;
  border: 0;
  color: #ffffff;
  background: linear-gradient(90deg, #2563eb 0%, #3b82f6 100%);

  &:hover,
  &:focus {
    background: linear-gradient(90deg, #1d4ed8 0%, #2563eb 100%);
    color: #ffffff;
  }
}

.login-form-title {
  min-height: 36px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 40px;

  .title-label {
    font-weight: 600;
    font-size: 22px;
    color: #0f172a;
  }
}
</style>
