import { shallowMount } from '@vue/test-utils'
import StockManage from '@/views/stock/index.vue'
import { adjustStock } from '@/api/stock'

jest.mock('@/api/product', () => ({ getProductPage: jest.fn(() => Promise.resolve({ data: { code: 1, data: { records: [], total: 0 } } })) }))
jest.mock('@/api/stock', () => ({ adjustStock: jest.fn(() => Promise.resolve({ data: { code: 1 } })) }))

it('sends the adjustment under the backend quantity field', async () => {
  const wrapper = shallowMount(StockManage, {
    mocks: { $message: { success: jest.fn(), error: jest.fn() } },
    stubs: ['el-tabs', 'el-tab-pane', 'el-input', 'el-button', 'el-dialog', 'el-form', 'el-form-item', 'el-input-number']
  })
  const vm: any = wrapper.vm
  vm.openAdjust({ id: 42, name: 'item', stock: 10 })
  vm.adjustData.changeQuantity = -2
  vm.adjustData.remark = 'correction'
  vm.$refs.adjustForm.validate = (callback: (valid: boolean) => void) => callback(true)

  vm.submitAdjust()

  expect(adjustStock).toHaveBeenCalledWith({ productId: 42, quantity: -2, remark: 'correction' })
  wrapper.destroy()
})

it('supplies a remark when the optional field is blank', () => {
  const wrapper = shallowMount(StockManage, {
    mocks: { $message: { success: jest.fn(), error: jest.fn() } },
    stubs: ['el-tabs', 'el-tab-pane', 'el-input', 'el-button', 'el-dialog', 'el-form', 'el-form-item', 'el-input-number']
  })
  const vm: any = wrapper.vm
  vm.openAdjust({ id: 42, name: 'item', stock: 10 })
  vm.adjustData.changeQuantity = 2
  vm.$refs.adjustForm.validate = (callback: (valid: boolean) => void) => callback(true)

  vm.submitAdjust()

  expect(adjustStock).toHaveBeenCalledWith({ productId: 42, quantity: 2, remark: '手动调整' })
  wrapper.destroy()
})
