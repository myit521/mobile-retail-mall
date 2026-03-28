import request from '@/utils/request'

/**
 * 员工管理 API
 */

// 登录
export const login = (data: any) =>
  request({
    url: '/employee/login',
    method: 'post',
    data
  })

// 退出
export const userLogout = (params: any) =>
  request({
    url: '/employee/logout',
    method: 'post',
    params
  })

// 员工分页查询
export const getEmployeeList = (params: any) => {
  return request({
    url: '/employee/page',
    method: 'get',
    params
  })
}

// 启用/禁用员工
export const enableOrDisableEmployee = (params: any) => {
  return request({
    url: `/employee/status/${params.status}`,
    method: 'post',
    params: { id: params.id }
  })
}

// 新增员工
export const addEmployee = (data: any) => {
  return request({
    url: '/employee',
    method: 'post',
    data
  })
}

// 修改员工
export const editEmployee = (data: any) => {
  return request({
    url: '/employee',
    method: 'put',
    data
  })
}

// 根据ID查询员工
export const queryEmployeeById = (id: string | (string | null)[]) => {
  return request({
    url: `/employee/${id}`,
    method: 'get'
  })
}

// 修改密码
export const editPassword = (data: any) => {
  return request({
    url: '/employee/editPassword',
    method: 'post',
    data
  })
}
