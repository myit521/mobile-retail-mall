import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

const store = new Vuex.Store({
	state: {
		storeInfo: {}, // 店铺请求的id信息
		shopInfo: '',  // 店铺详细信息
		orderListData: [],// 商品清单信息
		baseUserInfo: '', // 存储获取的用户微信的信息（用户名、头像）
		lodding: false,
		sessionId: '',
		dishTypeIndex: 0,
		shopPhone: '', //店铺电话
		shopStatus: {}, //店铺状态
		orderData: {},
		token: '',
		arrivals: '',
		remarkData: '',//备注
	},
	mutations: {
		setStoreInfo(state, provider) {
			state.storeInfo = provider
		},
		setShopInfo(state, provider) {
			state.shopInfo = provider
		},
		initdishListMut(state, provider) {
			state.orderListData = provider
		},
		setBaseUserInfo(state, provider) {
			state.baseUserInfo = provider
		},
		setLodding(state, provider) {
			state.lodding = provider
		},
		setSessionId(state, provider) {
			state.sessionId = provider
		},
		setDishTypeIndex(state, provider) {
			state.dishTypeIndex = provider
		},
		setShopPhone(state, provider) {
			state.shopPhone = provider
		},
		setShopStatus(state, provider) {
			state.shopStatus = provider
		},
		setOrderData(state, provider) {
			state.orderData = provider

		},
		setToken(state, provider) {
			state.token = provider
		},
		setArrivalTime(state, provider) {
			state.arrivals = provider
		},
		// 保存备注
		setRemark(state, provider) {
			state.remarkData = provider
		},
	},
	actions: {

	}
})

export default store
