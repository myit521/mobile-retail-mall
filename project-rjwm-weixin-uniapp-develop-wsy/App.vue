<script>
import store from './store/index.js'
import { userLogin } from './pages/api/api.js'

export default {
  onLaunch: function () {
    console.log('App Launch')
    // 尝试从 storage 恢复 token
    const token = uni.getStorageSync('token') || ''
    if (token) {
      store.commit('setToken', token)
      console.log('已从 storage 恢复 token')
    }
    // 自动登录
    this.autoLogin()
  },
  onShow: function () {
    console.log('App Show')
  },
  onHide: function () {
    console.log('App Hide')
  },
  methods: {
    // 自动登录
    async autoLogin() {
      try {
        // 1. 微信登录获取 code
        const loginRes = await this.wxLogin()
        if (!loginRes.code) {
          console.error('微信登录失败，未获取到 code')
          return
        }
        
        // 2. 调用后端登录接口
        const loginData = { code: loginRes.code }
        const res = await userLogin(loginData)
        
        if (res.code === 1 && res.data.token) {
          const token = res.data.token
          // 3. 保存 token 到 store 和 storage
          store.commit('setToken', token)
          uni.setStorageSync('token', token)
          console.log('自动登录成功，token 已保存')
        } else {
          console.error('登录接口返回失败:', res)
        }
      } catch (error) {
        console.error('自动登录异常:', error)
      }
    },
    
    // 封装微信登录
    wxLogin() {
      return new Promise((resolve) => {
        uni.login({
          provider: 'weixin',
          success: (loginRes) => {
            resolve(loginRes)
          },
          fail: (err) => {
            console.error('uni.login 失败:', err)
            resolve({ code: null })
          }
        })
      })
    }
  }
}
</script>

<style>
/*每个页面公共css */
/* #ifndef APP-PLUS-NVUE */
/* uni.css - 通用组件、模板样式库，可以当作一套ui库应用 */
/* 	    @import './common/uni.css'; */

/* H5 兼容 pc 所需 */
/* #ifdef H5 */
@media screen and (min-width: 768px) {
  body {
    overflow-y: scroll;
  }
}

/* 顶栏通栏样式 */
/* .uni-top-window {
		    left: 0;
		    right: 0;
		} */

uni-page-body {
  background-color: #f5f5f5 !important;
  min-height: 100% !important;
  height: auto !important;
}

.uni-top-window uni-tabbar .uni-tabbar {
  background-color: #fff !important;
}

.uni-app--showleftwindow .hideOnPc {
  display: none !important;
}
/* #endif */

/* 以下样式用于 hello uni-app 演示所需 */
page {
  background-color: #efeff4;
  height: 100%;
  font-size: 28rpx;
  line-height: 1.8;
  /* overflow: hidden; */
}
.fix-pc-padding {
  padding: 0 100rpx;
}
.uni-header-logo {
  padding: 30rpx;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  margin-top: 10rpx;
}

.uni-header-image {
  width: 200rpx;
  height: 200rpx;
}

.uni-hello-text {
  color: #7a7e83;
}

.uni-hello-addfile {
  text-align: center;
  line-height: 300rpx;
  background: #fff;
  padding: 50rpx;
  margin-top: 20rpx;
  font-size: 38rpx;
  color: #808080;
}
/* #endif*/

/*checkbox 选项框大小  */
/* uni-checkbox .uni-checkbox-input {
		width: 30rpx !important;
		height: 30rpx !important; 
	} */
/*checkbox选中后样式  */
/* uni-checkbox .uni-checkbox-input.uni-checkbox-input-checked {
		background: #3D7EFF;
		border-color:#3D7EFF;
	} */
/*checkbox选中后图标样式  */
/* uni-checkbox .uni-checkbox-input.uni-checkbox-input-checked::before {
		width: 20rpx;
		height: 20rpx;  
		line-height: 20rpx;
		text-align: center;
		font-size: 18rpx;
		color: #fff;
		background: transparent;
		transform: translate(-70%, -50%) scale(1);
		-webkit-transform: translate(-70%, -50%) scale(1);
	} */
</style>
