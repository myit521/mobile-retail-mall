import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { Message } from 'element-ui'
import { Route } from 'vue-router'
import { UserModule } from '@/store/modules/user'
import Cookies from 'js-cookie'

NProgress.configure({ 'showSpinner': false })

router.beforeEach(async (to: Route, _: Route, next: any) => {
  NProgress.start()
  if (Cookies.get('token')) {
    // 首屏恢复角色信息
    const roles = UserModule.roles
    if (!roles || roles.length === 0) {
      // 如果 roles 为空，尝试从 cookie 中恢复
      try {
        await UserModule.GetUserInfo()
      } catch (e) {
        // GetUserInfo 失败，清除 token 并跳转登录页
        Cookies.remove('token')
        Cookies.remove('user_info')
        NProgress.done()
        next('/login')
        return
      }
    }
    
    // 检查角色权限
    const currentRoles = UserModule.roles
    const toRoles = to.meta.roles as string[] | undefined
    
    if (toRoles && toRoles.length > 0) {
      // 需要特定角色才能访问
      const hasPermission = currentRoles.some(role => toRoles!.includes(role))
      if (!hasPermission) {
        NProgress.done()
        Message.error('权限不足，无法访问该页面')
        next({ path: '/dashboard' })
        return
      }
    }
    next()
  } else {
    if (!to.meta.notNeedAuth) {
      next('/login')
    } else {
      next()
    }
  }
})

router.afterEach((to: Route) => {
  NProgress.done()
  document.title = to.meta.title
})
