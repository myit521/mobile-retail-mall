import request from '@/utils/request'

/**
 * 数据概览（仪表盘）
 */
export const getOverview = () =>
  request({
    url: '/report/overview',
    method: 'get'
  })

/**
 * 营业额统计
 * @param params { begin: 'yyyy-MM-dd', end: 'yyyy-MM-dd' }
 */
export const getTurnoverStatistics = (params: any) =>
  request({
    url: '/report/turnover',
    method: 'get',
    params
  })

/**
 * 订单统计
 * @param params { begin: 'yyyy-MM-dd', end: 'yyyy-MM-dd' }
 */
export const getOrderStatistics = (params: any) =>
  request({
    url: '/report/orderStatistics',
    method: 'get',
    params
  })

/**
 * 商品销量排行
 * @param params { begin?: 'yyyy-MM-dd', end?: 'yyyy-MM-dd', limit?: number }
 */
export const getSalesRanking = (params: any) =>
  request({
    url: '/report/salesRanking',
    method: 'get',
    params
  })
