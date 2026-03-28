import request from '@/utils/request'

/**
 * 管理员端备忘录 API
 */

// 分页查询
export const getMemoPage = (params: any) => {
  return request({ url: '/memo/page', method: 'get', params })
}

// 新增备忘录
export const addMemo = (data: any) => {
  return request({ url: '/memo', method: 'post', data })
}

// 修改备忘录
export const editMemo = (data: any) => {
  return request({ url: '/memo', method: 'put', data })
}

// 删除备忘录
export const deleteMemo = (id: number | string) => {
  return request({ url: `/memo/${id}`, method: 'delete' })
}

// 更新状态
export const updateMemoStatus = (id: number | string, status: number) => {
  return request({ url: `/memo/${id}/status`, method: 'put', params: { status } })
}

// 快捷完成
export const completeMemo = (id: number | string) => {
  return request({ url: `/memo/${id}/complete`, method: 'put' })
}

// 快捷取消
export const cancelMemo = (id: number | string) => {
  return request({ url: `/memo/${id}/cancel`, method: 'put' })
}

// 获取统计
export const getMemoStats = () => {
  return request({ url: '/memo/stats', method: 'get' })
}

// AI 解析预览
export const parseMemoContent = (content: string) => {
  return request({ url: '/memo/parse', method: 'post', data: content,
    headers: { 'Content-Type': 'text/plain' } })
}
