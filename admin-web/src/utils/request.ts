import axios, { AxiosAdapter } from 'axios'
import { Message, MessageBox } from 'element-ui'
import { UserModule } from '@/store/modules/user'
import {
  getRequestKey,
  pending,
  checkPending,
  removePending
} from './requestOptimize'
import router from '@/router'
const CancelToken = axios.CancelToken;

const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API,
  'timeout': 600000
})

const NO_AUTH_URLS = ['/employee/login']

// Request interceptors
service.interceptors.request.use(
  (config: any) => {
    // console.log(config, 'config')
    // config.data = config.params
    // Add X-Access-Token header to every request, you can add other custom headers here
    if (UserModule.token) {
      config.headers['token'] = UserModule.token
    } else if (!NO_AUTH_URLS.includes(config.url)) {
      if (router.currentRoute.path !== '/login') {
        router.push('/login')
      }
      return Promise.reject(new axios.Cancel('NOT_LOGIN'))
    }

    // config.headers['Access-Control-Allow-Origin'] = '*'
    // config.headers['Content-Type'] = 'application/json;'
    // get请求映射params参数
    if (config.method === 'get' && config.params) {
      let url = config.url + '?';
      for (const propName of Object.keys(config.params)) {
        const value = config.params[propName];
        var part = encodeURIComponent(propName) + '=';
        if (value !== null && typeof (value) !== 'undefined') {
          if (typeof value === 'object') {
            for (const key of Object.keys(value)) {
              let params = propName + '[' + key + ']';
              var subPart = encodeURIComponent(params) + '=';
              url += subPart + encodeURIComponent(value[key]) + '&';
            }
          } else {
            url += part + encodeURIComponent(value) + '&';
          }
        }
      }
      url = url.slice(0, -1);
      config.params = {};
      config.url = url;
    }
    // 计算当前请求key值
    const key = getRequestKey(config);
    // console.log(pending,key,checkPending(key),'checkPending(key)')
    if (checkPending(key)) {
      // 重复请求则取消当前请求
      const source = CancelToken.source();
      config.cancelToken = source.token;
      source.cancel('重复请求');
    } else {
      // 加入请求字典
      pending[key] = true;
    }
    return config
  },
  (error: any) => {
    Promise.reject(error)
  }
)

// Response interceptors
service.interceptors.response.use(
  (response: any) => {
    // console.log(response, 'response')
    if (response.data.status === 401) {
      router.push('/login')
      // const res = response.data
      // return response
    }
    //请求响应中的config的url会带上代理的api需要去掉
    response.config.url = response.config.url.replace('/api', '')
    // 请求完成，删除请求中状态
    const key = getRequestKey(response.config);
    removePending(key);
    // if (response.data.code === 0) {
    //   Message.error(response.data.msg)
    //   // if(response.data.msg === 'NOTLOGIN' || response.data.msg === '未登录'){
    //   //   router.push('/login')
    //   // }
    //   // return window.location.href = '/login'
    //   // window.location.href = '/login'
    //   // return false
    // } else
    if (response.data.code === 1) {
      return response
    }
    Message.error(response.data.msg || '请求失败')
    return Promise.reject(response.data)
  },
  (error: any) => {
    // 被 CancelToken 取消的重复请求，静默忽略，不向业务层抛错
    if (axios.isCancel(error)) {
      return new Promise(() => {})
    }
    // console.log(error.config, pending, 'error')
    if (error && error.response) {
      switch (error.response.status) {
        case 401:
          router.push('/login')
          break;
        case 405:
          error.message = '请求错误'
      }
    }
    //请求响应中的config的url会带上代理的api需要去掉
    if (error && error.config) {
      error.config.url = error.config.url.replace('/api', '')
      // 请求完成，删除请求中状态
      const key = getRequestKey(error.config);
      removePending(key);
    }
    return Promise.reject(error)
  }
)

export default service
