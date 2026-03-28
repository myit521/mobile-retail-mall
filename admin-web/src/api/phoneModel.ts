import request from '@/utils/request'

/**
 * 手机型号管理 API
 */

// 手机型号分页查询
export const getPhoneModelPage = (params: any) => {
  return request({
    url: '/phoneModel/page',
    method: 'get',
    params
  })
}

// 新增手机型号
export const addPhoneModel = (data: any) => {
  return request({
    url: '/phoneModel',
    method: 'post',
    data
  })
}

// 修改手机型号
export const editPhoneModel = (data: any) => {
  return request({
    url: '/phoneModel',
    method: 'put',
    data
  })
}

// 根据ID查询手机型号
export const queryPhoneModelById = (id: string | number) => {
  return request({
    url: `/phoneModel/${id}`,
    method: 'get'
  })
}

// 批量删除手机型号
export const deletePhoneModel = (ids: any[]) => {
  return request({
    url: '/phoneModel',
    method: 'delete',
    params: { ids }
  })
}

// 启用/禁用手机型号
export const enableOrDisablePhoneModel = (params: any) => {
  return request({
    url: `/phoneModel/status/${params.status}`,
    method: 'post',
    params: { id: params.id }
  })
}

// 查询所有启用的手机型号（用于下拉选择）
export const getPhoneModelList = () => {
  return request({
    url: '/phoneModel/list',
    method: 'get'
  })
}
