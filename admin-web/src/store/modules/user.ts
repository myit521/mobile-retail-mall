import { VuexModule, Module, Action, Mutation, getModule } from 'vuex-module-decorators'
import { login, userLogout } from '@/api/employee'
import { getToken, setToken, removeToken, setUserInfo, getUserInfo, removeUserInfo } from '@/utils/cookies'
import store from '@/store'
import Cookies from 'js-cookie'
import { Message } from 'element-ui'

export interface IUserState {
  token: string
  name: string
  avatar: string
  introduction: string
  userInfo: any
  roles: string[]
  username: string
}

@Module({ 'dynamic': true, store, 'name': 'user' })
class User extends VuexModule implements IUserState {
  public token = getToken() || ''
  public name = ''
  public avatar = ''
  public introduction = ''
  public userInfo = {}
  public roles: string[] = []
  public username = Cookies.get('username') || ''

  @Mutation
  private SET_TOKEN(token: string) {
    this.token = token
  }

  @Mutation
  private SET_NAME(name: string) {
    this.name = name
  }

  @Mutation
  private SET_USERINFO(userInfo: any) {
    this.userInfo = { ...userInfo }
  }

  @Mutation
  private SET_AVATAR(avatar: string) {
    this.avatar = avatar
  }

  @Mutation
  private SET_INTRODUCTION(introduction: string) {
    this.introduction = introduction
  }

  @Mutation
  private SET_ROLES(roles: string[]) {
    this.roles = roles
  }

  @Mutation
  private SET_USERNAME(name: string) {
    this.username = name
  }

  @Action
  public async Login(userInfo: { username: string, password: string }) {
    let { username, password } = userInfo
    username = username.trim()
    this.SET_USERNAME(username)
    Cookies.set('username', username)
    const { data } = await login({ username, password })
    if (String(data.code) === '1') {
      this.SET_TOKEN(data.data.token)
      setToken(data.data.token)
      // 从后端返回的数据中获取角色
      const role = data.data.role || 'EMPLOYEE'
      this.SET_ROLES([role])
      this.SET_USERINFO(data.data)
      // 使用 JSON.stringify 序列化对象，避免存成 [object Object]
      Cookies.set('user_info', JSON.stringify(data.data))
      return data
    } else {
      return Message.error(data.msg)
    }
  }

  @Action
  public ResetToken() {
    removeToken()
    this.SET_TOKEN('')
    this.SET_ROLES([])
  }

  @Action
  public async GetUserInfo() {
    if (this.token === '') {
      throw Error('GetUserInfo: token is undefined!')
    }

    const rawInfo = getUserInfo()
    if (!rawInfo) {
      throw Error('Verification failed, please Login again.')
    }

    // 正确反序列化用户信息（后端返回 EmployeeLoginVO: id, userName, name, token, role）
    let data: any
    try {
      data = JSON.parse(rawInfo as string)
    } catch (e) {
      throw Error('Verification failed, please Login again.')
    }

    if (!data) {
      throw Error('Verification failed, please Login again.')
    }

    // 从后端返回的数据中获取角色
    const role = data.role || 'EMPLOYEE'
    const roles = [role]
    const { name, userName } = data

    this.SET_ROLES(roles)
    this.SET_USERINFO(data)
    this.SET_NAME(name || userName || '')
    this.SET_AVATAR('')
    this.SET_INTRODUCTION('')
  }

  @Action
  public async LogOut() {
    await userLogout({})
    removeToken()
    this.SET_TOKEN('')
    this.SET_ROLES([])
    Cookies.remove('username')
    Cookies.remove('user_info')
    removeUserInfo()
  }
}

export const UserModule = getModule(User)
