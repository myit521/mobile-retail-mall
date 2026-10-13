import axios from 'axios'
import request from '@/utils/request'
import { pending } from '@/utils/requestOptimize'
import { Message } from 'element-ui'

jest.mock('@/store/modules/user', () => ({ UserModule: { token: 'session-token' } }))
jest.mock('@/router', () => ({ currentRoute: { path: '/dashboard' }, push: jest.fn() }))
jest.mock('element-ui', () => ({ Message: { error: jest.fn() }, MessageBox: {} }))

beforeEach(() => {
  Object.keys(pending).forEach(key => delete pending[key])
  jest.clearAllMocks()
})

it('shows the server message on a non-2xx business response', async () => {
  const adapter = jest.fn(config => Promise.reject({
    config,
    message: 'Request failed with status code 400',
    response: { status: 400, data: { code: 'INVALID_STOCK', message: '库存不足' } }
  }))
  request.defaults.adapter = adapter as any

  await expect(request.get('/stock/alerts')).rejects.toMatchObject({ message: '库存不足' })
  expect(Message.error).toHaveBeenCalledWith('库存不足')
})

it('settles a duplicate request cancellation so the caller can clear loading', async () => {
  const adapter = jest.fn(() => new Promise(() => {}))
  request.defaults.adapter = adapter as any
  request.get('/stock/alerts')

  const result = await Promise.race([
    request.get('/stock/alerts').then(() => 'resolved', error => axios.isCancel(error) ? 'cancelled' : 'other-error'),
    new Promise(resolve => setTimeout(() => resolve('still-pending'), 50))
  ])

  expect(result).toBe('cancelled')
})
