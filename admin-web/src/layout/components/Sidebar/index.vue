<template>
  <div>
    <div class="logo">
      <div v-if="!isCollapse" class="sidebar-brand">手机商城</div>
      <div v-else class="sidebar-brand-mini">M</div>
    </div>
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu :default-openeds="defOpen"
               :default-active="defAct"
               :collapse="isCollapse"
               :background-color="variables.menuBg"
               :text-color="variables.menuText"
               :active-text-color="variables.menuActiveText"
               :unique-opened="false"
               :collapse-transition="false"
               mode="vertical">
        <sidebar-item v-for="route in routes"
                      :key="route.path"
                      :item="route"
                      :base-path="route.path"
                      :is-collapse="isCollapse" />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { AppModule } from '@/store/modules/app'
import { UserModule } from '@/store/modules/user'
import SidebarItem from './SidebarItem.vue'
import variables from '@/styles/_variables.scss'
import Cookies from 'js-cookie'

@Component({
  name: 'SideBar',
  components: {
    SidebarItem,
  },
})
export default class extends Vue {
  get name() {
    return (UserModule.userInfo as any).name
      ? (UserModule.userInfo as any).name
      : JSON.parse(Cookies.get('user_info') as any).name
  }

  get defOpen() {
    const path = ['/']
    this.routes.forEach((n: any) => {
      if (n.meta.roles && n.meta.roles[0] === this.roles[0]) {
        path.splice(0, 1, n.path)
      }
    })
    return path
  }

  get defAct() {
    return this.$route.path
  }

  get sidebar() {
    return AppModule.sidebar
  }

  get roles() {
    return UserModule.roles
  }

  get routes() {
    const routes = JSON.parse(JSON.stringify([...(this.$router as any).options.routes]))
    const menu = routes.find((item: any) => item.path === '/')
    if (!menu || !menu.children) return []
    
    // 根据角色过滤菜单
    const currentRole = this.roles[0]
    return menu.children.filter((route: any) => {
      const routeRoles = route.meta?.roles as string[] | undefined
      if (routeRoles && routeRoles.length > 0) {
        // 如果路由设置了角色要求，只有当前角色在允许列表中才显示
        return routeRoles.includes(currentRole)
      }
      // 没有设置角色要求的路由，默认都显示
      return true
    })
  }

  get variables() {
    return variables
  }

  get isCollapse() {
    return !this.sidebar.opened
  }
}
</script>

<style lang="scss" scoped>
.logo {
  text-align: center;
  background-color: #1890ff;
  padding: 15px 12px 0;
  height: 60px;
}

.sidebar-brand,
.sidebar-brand-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.32);
  background: rgba(255, 255, 255, 0.12);
}

.sidebar-brand {
  min-width: 120px;
  height: 31px;
  border-radius: 999px;
  font-size: 16px;
  letter-spacing: 2px;
}

.sidebar-brand-mini {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  font-size: 16px;
}

.el-scrollbar {
  height: 100%;
  background-color: rgb(52, 55, 68);
}

.el-menu {
  border: none;
  height: calc(95vh - 23px);
  width: 100% !important;
  padding: 47px 15px 0;
}
</style>