import store from './../store'
import { baseUrl } from './env'
// 参数： url:请求地址  param：请求参数  method：请求方式 callBack：回调函数
export function request({url='', params={}, method='GET'}) {
	const storeInfo = store.state
	let header = {
			'Accept': 'application/json',
			'Access-Control-Allow-Origin':'*',
			'Content-Type': 'application/json', 
			'authentication': storeInfo.token || ''
		}
	
	const requestRes = new Promise((resolve, reject) => {
		store.commit('setLodding', false)
		 uni.request({
			url: baseUrl+url, 
			data: params,
			header: header,
			method: method,
			success: (res) => {
				const { data } = res
				if (data.code == 200 || data.code === 1) {
					resolve(res.data)
				} else if (data.code === 401) {
					// token 失效，清除 token 并提示重新登录
					store.commit('setToken', '')
					uni.removeStorageSync('token')
					uni.showModal({
						title: '登录已过期',
						content: '需要重新登录，是否继续？',
						confirmText: '重新登录',
						success: (modalRes) => {
							if (modalRes.confirm) {
								// 触发重新登录：跳转首页会自动触发 App.vue 的自动登录
								uni.switchTab({
									url: '/pages/home/index'
								})
							}
						}
					})
					reject(data)
				} else {
					reject(res.data)
				}
			},
			fail: (err) => {
				const error = {data:{msg: err.errMsg || '网络请求失败'}}
				reject(error)
			}
		});
	})
	return requestRes
}

