import { shallowMount } from '@vue/test-utils'
import ProductLocation from '@/views/productLocation/index.vue'
import AddProduct from '@/views/product/addProduct.vue'
import { getProductLocationById, setProductLocation } from '@/api/productLocation'

jest.mock('@/api/productLocation', () => ({
  getProductLocationList: jest.fn(() => Promise.resolve({ data: { code: 1, data: [] } })),
  getProductLocationById: jest.fn(),
  setProductLocation: jest.fn(() => Promise.resolve({ data: { code: 1 } })),
  deleteProductLocation: jest.fn()
}))
jest.mock('@/api/product', () => ({
  getProductList: jest.fn(() => Promise.resolve({ data: { code: 1, data: [] } })),
  getCategoryListForProduct: jest.fn(() => Promise.resolve({ data: { code: 1, data: [] } }))
}))
jest.mock('@/api/phoneModel', () => ({ getPhoneModelList: jest.fn(() => Promise.resolve({ data: { code: 1, data: [] } })) }))
jest.mock('@/store/modules/user', () => ({ UserModule: { roles: ['ADMIN'] } }))

const mountView = (component: any) => shallowMount(component, {
  mocks: { $route: { query: {} }, $message: { success: jest.fn(), error: jest.fn() } },
  stubs: ['el-form', 'el-form-item', 'el-input', 'el-input-number', 'el-select', 'el-option', 'el-button', 'el-dialog', 'el-table', 'el-table-column', 'router-link']
})
beforeEach(() => jest.clearAllMocks())

it('binds location controls to positionCode and layerNum and submits those values', async () => {
  const wrapper = mountView(ProductLocation)
  const instance: any = wrapper.vm
  instance.openAdd()
  expect(instance.formData).toEqual({ productId: '', shelfCode: '', positionCode: '', layerNum: 1, remark: '' })
  instance.formData.productId = 7
  instance.formData.shelfCode = 'A'
  instance.formData.positionCode = 'A-03'
  instance.formData.layerNum = 3
  await instance.$nextTick()
  const inputs = wrapper.findAll('el-input-stub')
  inputs.at(inputs.length - 2).vm.$emit('input', 'A-04')
  wrapper.find('el-input-number-stub').vm.$emit('input', 4)
  instance.$refs.locationForm.validate = (callback: Function) => callback(true)
  instance.submitForm()
  expect(setProductLocation).toHaveBeenCalledWith({ productId: 7, shelfCode: 'A', positionCode: 'A-04', layerNum: 4, remark: '' })
  // Show the same backend fields in the table after a record is loaded.
  instance.tableData = [{ productId: 7, positionCode: 'A-04', layerNum: 4 }]
  await instance.$nextTick()
  expect(wrapper.findAll('el-table-column-stub').wrappers.map(column => column.attributes('prop'))).toEqual(expect.arrayContaining(['positionCode', 'layerNum']))
  wrapper.destroy()
})

it('loads saved product location into editable controls and preserves it in the save request', async () => {
  const wrapper = mountView(AddProduct)
  const instance: any = wrapper.vm
  ;(getProductLocationById as jest.Mock).mockResolvedValue({ data: { code: 1, data: { shelfCode: 'B', positionCode: 'B-05', layerNum: 5 } } })
  instance.loadLocation(7)
  await Promise.resolve()
  await instance.$nextTick()
  expect(instance.locationForm).toEqual({ shelfCode: 'B', positionCode: 'B-05', layerNum: 5 })
  const inputs = wrapper.findAll('el-input-stub')
  inputs.at(3).vm.$emit('input', 'B-06')
  const numbers = wrapper.findAll('el-input-number-stub')
  numbers.at(numbers.length - 1).vm.$emit('input', 6)
  instance.saveLocation(7)
  expect(setProductLocation).toHaveBeenCalledWith({ productId: 7, shelfCode: 'B', positionCode: 'B-06', layerNum: 6 })
  instance.resetForm()
  expect(instance.locationForm).toEqual({ shelfCode: '', positionCode: '', layerNum: 1 })
  wrapper.destroy()
})
