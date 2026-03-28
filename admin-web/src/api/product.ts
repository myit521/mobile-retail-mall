import request from '@/utils/request'

/**
 * 商品管理 API
 */

// 商品分页查询
export const getProductPage = (params: any) => {
  return request({
    url: '/product/page',
    method: 'get',
    params
  })
}

// 批量删除商品
export const deleteProduct = (ids: string) => {
  return request({
    url: '/product',
    method: 'delete',
    params: { ids }
  })
}

// 修改商品
export const editProduct = (data: any) => {
  return request({
    url: '/product',
    method: 'put',
    data
  })
}

// 新增商品
export const addProduct = (data: any) => {
  return request({
    url: '/product',
    method: 'post',
    data
  })
}

// 根据ID查询商品详情
export const queryProductById = (id: string | (string | null)[]) => {
  return request({
    url: `/product/${id}`,
    method: 'get'
  })
}

// 起售/停售商品
export const productStatusByStatus = (params: any) => {
  return request({
    url: `/product/status/${params.status}`,
    method: 'post',
    params: { id: params.id }
  })
}

// 按分类查询商品列表
export const getProductList = (categoryId?: number | string) => {
  return request({
    url: '/product/list',
    method: 'get',
    params: categoryId ? { categoryId } : {}
  })
}

// 查询分类列表（用于下拉选择）
export const getCategoryListForProduct = () => {
  return request({
    url: '/category/list',
    method: 'get'
  })
}
