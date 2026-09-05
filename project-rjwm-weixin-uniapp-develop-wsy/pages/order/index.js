import {
	mapState,
	mapMutations,
} from 'vuex'
import ProductDetail from "./components/dishDetail.vue" //商品明细
import OrderRemark from "./components/dishInfo.vue" //订单补充信息
export default {
	data() {
		return {
			orderDishPrice: 0,
			remark: '',//备注
			// 已选商品数量
			orderDishNumber: 0,
			showDisplay: false,//是否显示更多收起
		}
	},
	computed: {
		// 商品数据
		orderListDataes: function () {
			return this.orderListData()
		},
		// 商品数据
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
				if (sourceList.length > 3) {
					for (var i = 0; i < 3; i++) {
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
	components: {
		ProductDetail,
		OrderRemark
	},
	onLoad() {
		this.remark = this.remarkData()
		this.init()
	},
	methods: {
		...mapState(['orderListData', 'remarkData']),
		...mapMutations(['setRemark']),
		init() {
			this.computOrderInfo()
		},
		// 订单商品总价计算
		computOrderInfo() {
			let oriData = this.orderListDataes
			this.orderDishNumber = this.orderDishPrice = 0
			this.orderDishPrice = 0
			oriData.map((n) => {
				this.orderDishPrice += n.number * n.amount
				this.orderDishNumber += n.number
			})
		},
		// 返回上一级
		goBack() {
			// 尝试返回，如果没有历史记录则跳转到首页
			uni.navigateBack({
				delta: 1,
				fail: () => {
					uni.switchTab({
						url: '/pages/home/index'
					})
				}
			})
		},
		// 当前阶段不开放支付能力，先保留订单确认页展示
		payOrderHandle() {
			uni.showToast({
				title: '暂未开放支付功能',
				icon: 'none',
			})
		},
		// 进入备注页
		goRemark() {
			uni.redirectTo({
				url: '/pages/remark/index'
			})
		},
	}
}
