import request from '@/utils/request'

/**
 * 库存管理 API
 */

// 手动调整库存
export const adjustStock = (data: any) => {
  return request({
    url: '/stock/adjust',
    method: 'put',
    data
  })
}

// 分页查询库存日志
export const getStockLogs = (params: any) => {
  return request({
    url: '/stock/logs',
    method: 'get',
    params
  })
}

// 分页查询库存预警记录
export const getStockAlerts = (params: any) => {
  return request({
    url: '/stock/alerts',
    method: 'get',
    params
  })
}

// 处理预警（已处理/已忽略）
export const handleStockAlert = (id: number, status: number) => {
  return request({
    url: `/stock/alerts/${id}`,
    method: 'put',
    params: { status }
  })
}

// 创建盘点计划
export const createCheckPlan = (data: any) => {
  return request({
    url: '/stock/check/plan',
    method: 'post',
    data
  })
}

// 查询盘点计划列表
export const getCheckPlans = () => {
  return request({
    url: '/stock/check/plans',
    method: 'get'
  })
}

// 完成盘点计划
export const completeCheckPlan = (id: number) => {
  return request({
    url: `/stock/check/plan/${id}/complete`,
    method: 'put'
  })
}

// 提交盘点记录
export const submitCheckRecord = (data: any) => {
  return request({
    url: '/stock/check/record',
    method: 'post',
    data
  })
}

// 查询盘点记录
export const getCheckRecords = (planId: number) => {
  return request({
    url: `/stock/check/records/${planId}`,
    method: 'get'
  })
}

// 确认盘点记录并调整库存
export const confirmCheckRecord = (id: number) => {
  return request({
    url: `/stock/check/record/${id}/confirm`,
    method: 'put'
  })
}
