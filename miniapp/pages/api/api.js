import { request } from "../../utils/request.js"

// 用户登录
export const userLogin = (params) => {
	return request({
		url: '/user/user/login',
		method: 'POST',
		params
	})
}

// 分类列表
export const getCategoryList = (params) => {
	return request({
		url: '/user/category/list',
		method: 'GET',
		params
	})
}

// 商品列表
export const getProductListByCategoryId = (params) => {
	return request({
		url: '/user/product/list',
		method: 'GET',
		params
	})
}

// 兼容旧的列表命名
export const dishListByCategoryId = getProductListByCategoryId

// 商品搜索
export const searchProducts = (params) => {
	return request({
		url: '/user/product/search',
		method: 'GET',
		params
	})
}

// 文件预览/下载
export const commonDownload = (params) => {
	return request({
		url: '/user/common/download',
		method: 'GET',
		params
	})
}

// 获取购物车列表
export const getShoppingCartList = (params) => {
	return request({
		url: '/user/shoppingCart/list',
		method: 'GET',
		params
	})
}

// 购物车旧接口，暂保留兼容
export const addShoppingCart = (params) => {
	return request({
		url: '/user/shoppingCart',
		method: 'POST',
		params
	})
}

// 购物车旧接口，暂保留兼容
export const editHoppingCart = (params) => {
	return request({
		url: '/user/shoppingCart',
		method: 'PUT',
		params
	})
}

// 购物车新增
export const newAddShoppingCartAdd = (params) => {
	return request({
		url: '/user/shoppingCart/add',
		method: 'POST',
		params
	})
}

// 购物车减一
export const newShoppingCartSub = (params) => {
	return request({
		url: '/user/shoppingCart/sub',
		method: 'POST',
		params
	})
}

// 清空购物车
export const delShoppingCart = (params) => {
	return request({
		url: '/user/shoppingCart/clean',
		method: 'DELETE',
		params
	})
}

// 用户下单
export const submitOrderSubmit = (params) => {
	return request({
		url: '/user/order/submit',
		method: 'POST',
		params
	})
}

// 历史订单
export const getOrderPage = (params) => {
	return request({
		url: '/user/order/historyOrders',
		method: 'GET',
		params
	})
}

// 订单详情
export const getOrderDetail = (params) =>
	request({
		url: `/user/order/orderDetail/${params}`,
		method: 'GET'
	})

// 取消订单
export const cancelOrder = (params) =>
	request({
		url: `/user/order/cancel/${params}`,
		method: 'PUT'
	})

// 再来一单
export const repetitionOrder = (params) =>
	request({
		url: `/user/order/repetition/${params}`,
		method: 'POST',
		params
	})

// 店铺营业状态
export const getShopStatus = () => {
	return request({
		url: `/user/shop/status`,
		method: 'GET'
	})
}

// 手机型号列表
export const getPhoneModelList = (params) => {
	return request({
		url: '/user/phoneModel/list',
		method: 'GET',
		params
	})
}

// 按品牌查询手机型号
export const getPhoneModelListByBrand = (params) => {
	return request({
		url: '/user/phoneModel/listByBrand',
		method: 'GET',
		params
	})
}

// 按手机型号查询推荐商品
export const getProductListByPhoneModel = (params) => {
	return request({
		url: '/user/product/listByPhoneModel',
		method: 'GET',
		params
	})
}

