
import {
	getOrderDetail,
	repetitionOrder,
	delShoppingCart,
	cancelOrder,
} from '../api/api.js'
import { mapState, mapMutations } from 'vuex'
import { call } from '@/utils/index.js'
import Status from './components/status.vue'//订单状态
import OrderDetail from "./components/orderDetail.vue" //商品明细
import StoreInfo from "./components/storeInfo.vue" //门店信息
import OrderInfo from "./components/orderInfo.vue" //订单信息

export default {
	data() {
		return {
			showDisplay: false,
			rocallTime: '',
			textTip: '',
			showConfirm: false,
			orderDetailsData: {},
			timeout: false,
			orderId: null,
			times: null,
			phone: ""
		}
	},

	computed: {
		orderListDataes: function () {
			return this.orderListData()
		},

		// // 处理订单详情列表
		orderDataes: function () {
			const normalizeImage = (image) => {
				if (!image || typeof image !== 'string') {
					return '/static/imgDefault.png'
				}
				const value = image.trim()
				if (!value || value.indexOf('/pages/') === 0 || value.indexOf('pages/') === 0) {
					return '/static/imgDefault.png'
				}
				if (value.indexOf('http://') === 0 || value.indexOf('https://') === 0) {
					return value
				}
				if (value.indexOf('/') === 0) {
					return '/static/imgDefault.png'
				}
				return value
			}
			const sourceList = Array.isArray(this.orderListDataes)
				? this.orderListDataes.map((item) => ({
					...item,
					image: normalizeImage(item.image),
				}))
				: []
			let testList = []
			if (this.showDisplay === false) {
				if (sourceList.length > 2) {
					for (var i = 0; i < 2; i++) {
						testList.push(sourceList[i])
					}
				} else {
					testList = sourceList
				}
				return testList
			} else {
				return sourceList
			}
		}
	},
	//   组件
	components: { Status, OrderDetail, StoreInfo, OrderInfo },
	onLoad(options) {
		this.getBaseData(options.orderId)
	},

	methods: {
		...mapMutations(['initdishListMut']),
		...mapState(['orderListData']),
		// 获取订单详情
		getBaseData(id) {
			getOrderDetail(id).then(res => {
				if (res.code === 1) {
					this.orderDetailsData = res.data
					const orderDetailList = Array.isArray(this.orderDetailsData.orderDetailList)
						? this.orderDetailsData.orderDetailList.map((item) => ({
							...item,
							image: (!item.image || (typeof item.image === 'string' && (item.image.trim().indexOf('/pages/') === 0 || item.image.trim().indexOf('pages/') === 0 || item.image.trim().indexOf('/') === 0 && item.image.trim().indexOf('/static/') !== 0))) ? '/static/imgDefault.png' : item.image,
						}))
						: []
					this.initdishListMut(orderDetailList)
					if (this.orderDetailsData.status === 1) {
						this.runTimeBack(this.orderDetailsData.orderTime)
					}
				}
			})
		},

		// 取消订单接口
		cancel(type, obj) {
			cancelOrder(obj.id).then(res => {
				if (res.code === 1) {
					this.showConfirm = true
					this.textTip = '当前订单已取消！'
					this.openPopup(type)
					this.orderId = obj.id
				}
			})
		},
		// 取消订单
		handleCancel(val) {
			if (val.obj.status === 1 || val.obj.status === 2) {
				this.cancel(val.type, val.obj)
			} else {
				this.showConfirm = false
				this.textTip = '当前状态下请联系店铺处理取消。'
				this.openPopup(val.type)
			}
		},
		// 再来一单
		async oneMoreOrder(id) {
			// 先清空购物车
			await delShoppingCart()
			repetitionOrder(id).then(res => {
				if (res.code === 1) {
					uni.redirectTo({
						url: '/pages/index/index'
					})
				}
			})
		},
		// 处理状态
		statusWord(status) {
			if ((this.timeout && status === 1) || this.orderDetailsData.status === 6) {
				return '订单已取消'
			}
			switch (status) {
				case 2:
					return '待确认'
				case 3:
					return '已确认'
				case 4:
					return '处理中'
				case 5:
					return '订单已完成'
				default:
					return '处理中'
			}
		},
		// 订单倒计时
		runTimeBack(time) {
			const end = Date.parse(time.replace(/-/g, "/"))

			const now = Date.parse(new Date())
			const m15 = 15 * 60 * 1000
			const msec = m15 - (now - end)
			if (msec < 0) {
				this.timeout = true
				clearTimeout(this.times)
				this.cancel('center', this.orderDetailsData) //超时的时候取消订单
			} else {
				let min = parseInt(msec / 1000 / 60 % 60)
				let sec = parseInt(msec / 1000 % 60)
				if (min < 10) {
					min = '0' + min
				} else {
					min = min
				}
				if (sec < 10) {
					sec = '0' + sec
				} else {
					sec = sec
				}
				this.rocallTime = min + ':' + sec
				let that = this
				if (min >= 0 && sec >= 0) {
					if (min === 0 && sec === 0) {
						this.timeout = true
						clearTimeout(this.times)
						this.cancel('center', this.orderDetailsData) //超时的时候取消订单
						return
					}
					this.times = setTimeout(function () {
						that.runTimeBack(time)
					}, 1000)
				}
			}
		},

		// 返回上一级
		goBack() {
			uni.redirectTo({
				url: '/pages/historyOrder/historyOrder'
			})
		},
		openPopup(type) {
			this.$refs.commonPopup.open(type)
		},
		// 当前版本不开放线上售后入口
		handleRefund(type) {
			this.showConfirm = false
			this.textTip = '当前版本暂未开放线上售后，请联系店铺处理。'
			this.openPopup(type)
		},
		// 拨打电话弹层
		handlePhone(type, phone) {
			// 暂时关闭打电话
			this.$refs.phone.open(type)

			this.phone = phone;
		},
		// 关闭弹层
		closePopup(type) {
			this.$refs.phone.close(type)
		},
		// closePopupInfo
		closePopupInfo(type) {
			this.$refs.commonPopup.close(type)
			if (this.orderId) {
				this.getBaseData(this.orderId)
			}
		},
		// 支付功能暂未开放
		handlePay() {
			uni.showToast({
				title: '暂未开放支付功能',
				icon: 'none'
			})
		},
		call() {
			call(this.phone)
		},
	}
}
