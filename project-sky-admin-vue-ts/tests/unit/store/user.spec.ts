import Cookies from 'js-cookie'
import { UserModule } from '@/store/modules/user'
import { login } from '@/api/employee'

jest.mock('@/api/employee', () => ({ login: jest.fn(), userLogout: jest.fn() }))

it('restores the logged-in role and name from the same cookie after refresh', async () => {
  const employee = { token: 'session-token', role: 'ADMIN', name: 'Manager', userName: 'manager' }
  ;(login as jest.Mock).mockResolvedValue({ data: { code: 1, data: employee } })

  await UserModule.Login({ username: 'manager', password: 'password' })
  ;(UserModule as any).SET_ROLES([])
  ;(UserModule as any).SET_USERINFO({})
  await UserModule.GetUserInfo()

  expect(UserModule.roles).toEqual(['ADMIN'])
  expect(UserModule.userInfo).toEqual(employee)
  expect(UserModule.name).toBe('Manager')
  Cookies.remove('username')
  Cookies.remove('user_info')
  Cookies.remove('userInfo')
  Cookies.remove('token')
})
