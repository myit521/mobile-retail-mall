import request from '@/utils/request'

/**
 * 商品位置管理 API
 */

// 设置商品位置（新增或更新）
export const setProductLocation = (data: any) => {
  return request({
    url: '/productLocation',
    method: 'post',
    data
  })
}

// 根据商品ID查询位置
export const getProductLocationById = (productId: number) => {
  return request({
    url: `/productLocation/${productId}`,
    method: 'get'
  })
}

// 查询商品位置列表
export const getProductLocationList = (shelfCode?: string) => {
  return request({
    url: '/productLocation/list',
    method: 'get',
    params: shelfCode ? { shelfCode } : {}
  })
}

// 删除商品位置
export const deleteProductLocation = (productId: number) => {
  return request({
    url: `/productLocation/${productId}`,
    method: 'delete'
  })
}
