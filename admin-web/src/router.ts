import Vue from "vue";
import Router from "vue-router";
import Layout from "@/layout/index.vue";

Vue.use(Router);

const router = new Router({
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return { x: 0, y: 0 };
  },
  base: process.env.BASE_URL,
  routes: [
    {
      path: "/login",
      component: () =>
        import(/* webpackChunkName: "login" */ "@/views/login/index.vue"),
      meta: { title: "智能商城管理系统", hidden: true, notNeedAuth: true }
    },
    {
      path: "/404",
      component: () => import(/* webpackChunkName: "404" */ "@/views/404.vue"),
      meta: { title: "智能商城管理系统", hidden: true, notNeedAuth: true }
    },
    {
      path: "/",
      component: Layout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          component: () =>
            import(/* webpackChunkName: "dashboard" */ "@/views/dashboard/index.vue"),
          name: "Dashboard",
          meta: {
            title: "工作台",
            icon: "dashboard",
            affix: true
          }
        },
        {
          path: "/statistics",
          component: () =>
            import(/* webpackChunkName: "statistics" */ "@/views/statistics/index.vue"),
          meta: {
            title: "数据统计",
            icon: "icon-statistics"
          }
        },
        {
          path: "order",
          component: () =>
            import(/* webpackChunkName: "order" */ "@/views/orderDetails/index.vue"),
          meta: {
            title: "订单管理",
            icon: "icon-order"
          }
        },
        {
          path: "product",
          component: () =>
            import(/* webpackChunkName: "product" */ "@/views/product/index.vue"),
          meta: {
            title: "商品管理",
            icon: "icon-dish"
          }
        },
        {
          path: "/product/add",
          component: () =>
            import(/* webpackChunkName: "product" */ "@/views/product/addProduct.vue"),
          meta: {
            title: "添加商品",
            hidden: true
          }
        },
        {
          path: "phoneModel",
          component: () =>
            import(/* webpackChunkName: "phoneModel" */ "@/views/phoneModel/index.vue"),
          meta: {
            title: "手机型号管理",
            icon: "icon-category"
          }
        },
        {
          path: "category",
          component: () =>
            import(/* webpackChunkName: "category" */ "@/views/category/index.vue"),
          meta: {
            title: "分类管理",
            icon: "icon-category"
          }
        },
        {
          path: "stock",
          component: () =>
            import(/* webpackChunkName: "stock" */ "@/views/stock/index.vue"),
          meta: {
            title: "库存管理",
            icon: "icon-statistics"
          }
        },
        {
          path: "productLocation",
          component: () =>
            import(/* webpackChunkName: "productLocation" */ "@/views/productLocation/index.vue"),
          meta: {
            title: "商品位置管理",
            icon: "icon-order"
          }
        },
        {
          path: "employee",
          component: () =>
            import(/* webpackChunkName: "employee" */ "@/views/employee/index.vue"),
          meta: {
            title: "员工管理",
            icon: "icon-employee",
            roles: ["ADMIN"]
          }
        },
        {
          path: "memo",
          component: () =>
            import(/* webpackChunkName: "memo" */ "@/views/memo/index.vue"),
          meta: {
            title: "备忘录",
            icon: "icon-order"
          }
        },
        {
          path: "/employee/add",
          component: () =>
            import(/* webpackChunkName: "employee" */ "@/views/employee/addEmployee.vue"),
          meta: {
            title: "添加员工",
            hidden: true,
            roles: ["ADMIN"]
          }
        }
      ]
    },
    {
      path: "*",
      redirect: "/404",
      meta: { hidden: true }
    }
  ]
});

export default router;
