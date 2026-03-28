import request from '@/utils/request'

/**
 * 分类管理 API
 */

// 查询分类分页列表
export const getCategoryPage = (params: any) => {
  return request({
    url: '/category/page',
    method: 'get',
    params
  })
}

// 删除分类
export const deleCategory = (id: string) => {
  return request({
    url: '/category',
    method: 'delete',
    params: { id }
  })
}

// 修改分类
export const editCategory = (data: any) => {
  return request({
    url: '/category',
    method: 'put',
    data
  })
}

// 新增分类
export const addCategory = (data: any) => {
  return request({
    url: '/category',
    method: 'post',
    data
  })
}

// 启用/禁用分类
export const enableOrDisableCategory = (params: any) => {
  return request({
    url: `/category/status/${params.status}`,
    method: 'post',
    params: { id: params.id }
  })
}

// 查询所有分类列表（用于下拉）
export const getCategoryList = () => {
  return request({
    url: '/category/list',
    method: 'get'
  })
}
