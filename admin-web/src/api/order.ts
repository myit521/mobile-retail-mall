import request from '@/utils/request'

/**
 * 订单管理 API
 */

// 查询订单列表
export const getOrderDetailPage = (params: any) => {
  return request({
    url: '/order/conditionSearch',
    method: 'get',
    params
  })
}

// 查询订单详情
export const queryOrderDetailById = (params: any) => {
  return request({
    url: `/order/details/${params.orderId}`,
    method: 'get'
  })
}

// 取消订单
export const orderCancel = (data: any) => {
  return request({
    url: '/order/cancel',
    method: 'put',
    data
  })
}

// 确认订单
export const orderAccept = (data: any) => {
  return request({
    url: '/order/confirm',
    method: 'put',
    data
  })
}

// 驳回订单
export const orderReject = (data: any) => {
  return request({
    url: '/order/rejection',
    method: 'put',
    data
  })
}

// 获取各状态订单数量统计
export const getOrderListBy = () => {
  return request({
    url: '/order/statistics',
    method: 'get'
  })
}
