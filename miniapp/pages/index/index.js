import navBar from "../common/Navbar/navbar.vue" //标题
import Phone from "@/components/uni-phone/index.vue" //拨打电话
import popMask from "./components/popMask.vue" //规格
import popCart from "./components/popCart.vue" //购物车弹出层
import ProductDetail from "./components/dishDetail.vue" //商品详情
import {
	// 当前首页使用的接口
	userLogin,
	getCategoryList,
	getProductListByCategoryId,
	// 获取购物车集合
	getShoppingCartList,
	// 新的购物车添加逻辑接口
	newAddShoppingCartAdd,
	// 新的购物车减少接口
	newShoppingCartSub,
	// 清空购物车
	delShoppingCart,
	// 获取店铺信息
	getShopStatus,
} from "../api/api.js"
import { mapState, mapMutations } from "vuex"
export default {
	data() {
		return {
			// 去结算部分
			openOrderCartList: false,
			// 左侧分类列表
			typeListData: [],
			dishListData: [],
			// 当前分类对应的商品列表
			dishListItems: [],
			productDetails: {},
			openDetailPop: false,
			openMoreNormPop: false,
			moreNormDishdata: null,
			moreNormdata: null,
			// 选中左侧分类的索引
			typeIndex: 0,
			// 规格有关的数组
			flavorDataes: [],
			// 已选商品数量
			orderDishNumber: 0,
			// 商品金额
			orderDishPrice: 0,
			// 记录当前分类，便于加购后按原分类刷新列表
			rightIdAndType: {},
			phoneData: "暂未提供联系电话",
			shopStatus: null,
			scrollTop: 0,
			menuHeight: 0, // 左边菜单的高度
			menuItemHeight: 0, // 左边菜单item的高度
			itemId: "", // 栏目右边scroll-view用于滚动的id
			arr: [],
		}
	},
	//   组件
	components: {
		navBar,
		Phone,
		popMask,
		popCart,
		ProductDetail,
	},
	//   计算属性
	computed: {
		// 购物车信息列表
		orderListDataes: function () {
			return this.orderListData()
		},
		loaddingSt: function () {
			return this.lodding()
		},
		// 计算购物车清单
		orderAndUserInfo: function () {
			let orderData = []
			Array.isArray(this.orderListDataes) &&
				this.orderListDataes.forEach((n, i) => {
					let userData = {}
					userData.nickName = n && n.name ? n.name : ""
					userData.avatarUrl = n && n.image ? n.image : ""
					userData.dishList = [n]
					const num = orderData.findIndex(
						(o) => o.nickName == userData.nickName
					)
					if (num != -1) {
						orderData[num].dishList.push(n)
					} else {
						orderData.push(userData)
					}
				})
			return orderData
		},
		ht: function () {
			return (
				uni.getMenuButtonBoundingClientRect().top +
				uni.getMenuButtonBoundingClientRect().height +
				7
			)
		},
	},

	onReady() {
		this.getMenuItemTop()
	},
	onLoad(options) {
		uni.onNetworkStatusChange(function (res) {
			if (res.isConnected == false) {
				uni.navigateTo({
					url: "/pages/nonet/index",
				})
			}
		})
		if (options) {
			if (!options.status && !options.formOrder) {
				this.getData()
			}
		}
	},
	onShow() {
		if (this.token()) {
			this.init()
		}
	},
	methods: {
		//   vuex储存信息
		...mapMutations([
			"setShopInfo", //设置店铺信息
			"setShopStatus", //设置店铺状态
			"initdishListMut", //设置购物车订单
			"setBaseUserInfo", //设置用户基本信息
			"setLodding",
			"setToken", //设置token
		]),
		// 从vuex信息
		...mapState([
			"shopInfo", //店铺信息
			"orderListData",
			"baseUserInfo", //用户信息
			"lodding",
			"token", //token
		]),
		handleSearchClick() {
			uni.navigateTo({
				url: "/pages/search/index",
				events: {
					selectProduct: (product) => {
						this.focusProductFromSearch(product)
					},
				},
			})
		},
		async focusProductFromSearch(product) {
			if (!product || !product.id) {
				return
			}
			if (!this.typeListData || this.typeListData.length === 0) {
				await this.init()
			}
			const categoryIndex = this.typeListData.findIndex(
				(item) => Number(item.id) === Number(product.categoryId)
			)
			if (categoryIndex === -1) {
				uni.showToast({
					title: "未找到商品所在分类",
					icon: "none",
				})
				return
			}
			const category = this.typeListData[categoryIndex]
			await this.leftMenuStatus(categoryIndex)
			await this.getDishListDataes(category, categoryIndex)
			this.$nextTick(() => {
				const target = (this.dishListItems || []).find(
					(item) => Number(item.id) === Number(product.id)
				)
				if (!target) {
					uni.showToast({
						title: "商品已下架或当前不可见",
						icon: "none",
					})
					return
				}
				this.openDetailHandle(target)
			})
		},
		// 获取用户信息
		getData() {
			// 获取店铺状态
			this.getShopInfo()
			if (!this.token()) {
				uni.showToast({
					title: "请先从首页完成微信授权登录",
					icon: "none",
				})
				return
			}
			this.init()
		},

		async init() {
			// 获取分类接口
			if (this.typeIndex !== 0) {
				this.typeIndex = 0
			}

			await getCategoryList().then((res) => {
				if (res && res.code === 1) {
					this.typeListData = [...res.data]
					if (res.data.length > 0) {
						return this.getDishListDataes(res.data[this.typeIndex || 0])
					}
					this.dishListData = []
					this.dishListItems = []
					return null
				}
				this.typeListData = []
				this.dishListData = []
				this.dishListItems = []
				return null
			})
			// 调用一次购物车集合---初始化
			await this.getTableOrderDishListes()
		},
		// 点击左边的栏目切换
		async swichMenu(params, index) {
			if (this.arr.length == 0) {
				await this.getMenuItemTop()
			}
			if (index == this.typeIndex) return
			this.$nextTick(function () {
				this.typeIndex = index
				this.leftMenuStatus(index)
			})
			this.getDishListDataes(params, index)
		},
		// 获取一个目标元素的高度
		getElRect(elClass, dataVal) {
			new Promise((resolve, reject) => {
				const query = uni.createSelectorQuery().in(this)
				query
					.select("." + elClass)
					.fields(
						{
							size: true,
						},
						(res) => {
							// 如果节点尚未生成，res值为null，循环调用执行
							if (!res) {
								setTimeout(() => {
									this.getElRect(elClass)
								}, 10)
								return
							}
							this[dataVal] = res.height
							resolve()
						}
					)
					.exec()
			})
		},
		// 设置左边菜单的滚动状态
		async leftMenuStatus(index) {
			this.typeIndex = index
			// 如果为0，意味着尚未初始化
			if (this.menuHeight == 0 || this.menuItemHeight == 0) {
				await this.getElRect("menu-scroll-view", "menuHeight")
				await this.getElRect("type_item", "menuItemHeight")
			}
			// 将菜单活动item垂直居中
			this.scrollTop =
				index * this.menuItemHeight +
				this.menuItemHeight / 2 -
				this.menuHeight / 2
		},
		// 获取右边菜单每个item到顶部的距离
		getMenuItemTop() {
			new Promise((resolve) => {
				let selectorQuery = uni.createSelectorQuery()
				selectorQuery
					.selectAll(".type_item")
					.boundingClientRect((rects) => {
						// 如果节点尚未生成，rects值为[](因为用selectAll，所以返回的是数组)，循环调用执行
						if (!rects.length) {
							setTimeout(() => {
								this.getMenuItemTop()
							}, 10)
							return
						}
					})
					.exec()
			})
		},
		// 规范商品图片地址，避免后端残留本地演示图片路径或失效 OSS 地址导致小程序报错
		normalizeProductImage(image) {
			const defaultImage = '/static/imgDefault.png'
			if (!image || typeof image !== 'string') {
				return defaultImage
			}
			const value = image.trim()
			if (!value) {
				return defaultImage
			}
			if (value.indexOf('/pages/') === 0 || value.indexOf('pages/') === 0) {
				return defaultImage
			}
			if (value.indexOf('http://') === 0 || value.indexOf('https://') === 0) {
				return value
			}
			if (value.indexOf('/') === 0) {
				return defaultImage
			}
			return value
		},
		handleListImageError(item) {
			if (item && item.image !== '/static/imgDefault.png') {
				this.$set(item, 'image', '/static/imgDefault.png')
			}
		},
		// 获取商品列表
		async getDishListDataes(params, index) {
			const currentIndex = typeof index === "number" ? index : this.typeIndex
			this.rightIdAndType = {
				id: params.id,
				index: currentIndex,
			}
			const param = {
				categoryId: params.id,
			}
			await getProductListByCategoryId(param)
				.then((res) => {
					if (res && res.code === 1) {
						this.dishListData =
							res.data &&
							res.data.map((obj) => ({
								...obj,
								image: this.normalizeProductImage(obj.image),
								type: 1,
								flavors: (obj.specList || []).map((spec) => ({
									name: spec.specName,
									value: JSON.stringify([spec.specValue]),
								})),
								newCardNumber: 0,
							}))
					} else {
						this.dishListData = []
					}
				})
				.catch((err) => {
					this.dishListData = []
					this.dishListItems = []
				})
			this.typeIndex = currentIndex
			this.setOrderNum()
		},
		// 获取首页店铺信息
		async getShopInfo() {
			await getShopStatus()
				.then((res) => {
					this.shopStatus = res.data
					this.setShopStatus(res.data)
				})
				.catch((err) => { })
		},
		// 获取购物车订单列表
		async getTableOrderDishListes() {
			// 调用获取购物车集合接口
			await getShoppingCartList({})
				.then((res) => {
					if (res.code === 1) {
						const cartList = Array.isArray(res.data)
							? res.data.map((item) => ({
								...item,
								image: this.normalizeProductImage(item.image),
							}))
							: []
						this.initdishListMut(cartList)
						this.computOrderInfo()
					}
				})
				.catch((err) => { })
		},
		// 加入购物车
		async addDishAction(item, form) {
			if (item.stock !== undefined && item.stock !== null && Number(item.stock) <= 0) {
				uni.showToast({
					title: "当前商品暂无库存",
					icon: "none",
				})
				return false
			}
			// 规格
			if (
				this.openMoreNormPop &&
				(!this.flavorDataes || this.flavorDataes.length <= 0)
			) {
				uni.showToast({
					title: "请选择规格",
					icon: "none",
				})
				return false
			}
			this.openMoreNormPop = false
			// 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
			if (this.productDetails && typeof this.productDetails.dishNumber === 'number') {
				this.productDetails.dishNumber++
			}
			if (
				this.orderListDataes &&
				!this.orderListDataes.some((n) => n.productId == item.id) &&
				this.flavorDataes.length > 0
			) {
				item.flavorRemark = JSON.stringify(this.flavorDataes)
			}
			let specInfo = ""
			let flavorRemark = []
			if (item.flavorRemark) {
				flavorRemark = JSON.parse(item.flavorRemark)
			}
			if (item.specInfo) {
				specInfo = item.specInfo
			} else if (flavorRemark.length > 0) {
				specInfo = flavorRemark.map((spec) => `${spec.specName}:${spec.specValue}`).join('；')
			} else {
				specInfo = ""
			}
			let params = {
				productId: item.id,
				specInfo,
			}
			if (form === "购物车") {
				params = {
					productId: item.productId,
					specInfo: item.specInfo || "",
				}
			}
			newAddShoppingCartAdd(params)
				.then((res) => {
					if (res.code === 1) {
						// 调用一次购物车集合---初始化
						this.getTableOrderDishListes()
						// 重新拉取当前分类商品列表
						this.getDishListDataes(this.rightIdAndType, this.rightIdAndType.index)
						this.flavorDataes = []
					}
				})
				.catch((err) => { })
		},
		// 加入购物车
		addShop(item) {
			this.productDetails = item
			this.addDishAction(item, "普通")
		},
		// 从购物车减少商品
		async redDishAction(item, form) {
			if (this.productDetails && typeof this.productDetails.dishNumber === 'number' && this.productDetails.dishNumber > 0) {
				this.productDetails.dishNumber--
			}
			let specInfo = ""
			let flavorRemark = []
			if (item.flavorRemark) {
				flavorRemark = JSON.parse(item.flavorRemark)
			}
			if (item.specInfo) {
				specInfo = item.specInfo
			} else if (flavorRemark.length > 0) {
				specInfo = flavorRemark.map((spec) => `${spec.specName}:${spec.specValue}`).join('；')
			} else {
				specInfo = ""
			}
			let params = {
				productId: item.id,
				specInfo,
			}
			if (form === "购物车") {
				params = {
					productId: item.productId,
					specInfo: item.specInfo || "",
				}
			}
			await newShoppingCartSub(params)
				.then((res) => {
					if (res.code === 1) {
						// 调用一次购物车集合---初始化
						this.getTableOrderDishListes()
						// 重新拉取当前分类商品列表
						this.getDishListDataes(this.rightIdAndType, this.rightIdAndType.index)
					}
				})
				.catch((err) => { })
		},
		// 清空购物车
		clearCardOrder() {
			delShoppingCart()
				.then((res) => {
					this.openOrderCartList = false
					// 调用一次购物车集合---初始化
					this.getTableOrderDishListes()
					// 重新拉取当前分类商品列表
					this.getDishListDataes(this.rightIdAndType, this.rightIdAndType.index)
				})
				.catch((err) => { })
		},
		// 打开商品详情
		openDetailHandle(item) {
			this.productDetails = item
			this.openDetailPop = true
		},
		// 关闭商品详情
		dishClose() {
			this.openDetailPop = false
		},
		// 多规格数据处理
		moreNormDataesHandle(item) {
			this.flavorDataes.splice(0)
			this.moreNormDishdata = item
			this.openDetailPop = false
			this.openMoreNormPop = true
			this.moreNormdata = (item.flavors || []).map((obj) => ({
				...obj,
				value: typeof obj.value === 'string' ? JSON.parse(obj.value) : (obj.value || []),
			}))
			this.moreNormdata.forEach((item) => {
				if (item.value && item.value.length > 0) {
					this.flavorDataes.push({
						specName: item.name,
						specValue: item.value[0],
					})
				}
			})
		},
		// 选规格 处理一行只能选择一种
		checkMoreNormPop(val) {
			const specName = val.specName
			const item = val.item
			const currentIndex = this.flavorDataes.findIndex((it) => it.specName === specName)
			const selected = {
				specName,
				specValue: item,
			}
			if (currentIndex === -1) {
				this.flavorDataes.push(selected)
			} else {
				this.$set(this.flavorDataes, currentIndex, selected)
			}
		},
		// 关闭规格弹窗
		closeMoreNorm() {
			this.flavorDataes.splice(0, this.flavorDataes.length)
			this.openMoreNormPop = false
		},
		// 订单里和总订单价格计算
		computOrderInfo() {
			let oriData = this.orderListDataes
			this.orderDishNumber = this.orderDishPrice = 0
			oriData.map((n, i) => {
				this.orderDishNumber += n.number
				this.orderDishPrice += n.number * n.amount
			})
			this.orderDishPrice = this.orderDishPrice
		},
		// 更新商品已加入购物车数量
		setOrderNum() {
			let ODate = this.dishListData
			let CData = this.orderListDataes
			ODate &&
				ODate.map((obj) => {
					obj.dishNumber = 0
					obj.flavors = (obj.flavors || []).filter((value) => value && value.name)

					if (CData.length > 0) {
						CData &&
							CData.forEach((tg) => {
								if (obj.id === tg.productId) {
									obj.dishNumber = tg.number
								}
							})
					}
				})
			if (this.dishListItems.length == 0) {
				this.dishListItems = ODate
			} else {
				this.dishListItems.splice(0, this.dishListItems.length, ...ODate)
			}
		},
		// 拨打电话弹层
		handlePhone(type) {
			this.$refs.phone.$refs.popup.open(type)
		},
		// 关闭电话弹层
		closePopup(type) {
			this.$refs.phone.$refs.popup.close(type)
		},
		disabledScroll() {
			return false
		},
	},
}
