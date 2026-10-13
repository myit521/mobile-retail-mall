import { shallowMount } from '@vue/test-utils'
import AddProduct from '@/views/product/addProduct.vue'
import { addProduct, editProduct } from '@/api/product'

jest.mock('@/api/product', () => ({
  getCategoryListForProduct: jest.fn(() => Promise.resolve({ data: { code: 1, data: [] } })),
  queryProductById: jest.fn(() => Promise.resolve({ data: { code: 1, data: { id: 42, stock: 10, price: 1 } } })),
  addProduct: jest.fn(() => Promise.resolve({ data: { code: 1 } })),
  editProduct: jest.fn(() => Promise.resolve({ data: { code: 1 } }))
}))
jest.mock('@/api/phoneModel', () => ({ getPhoneModelList: jest.fn(() => Promise.resolve({ data: { code: 1, data: [] } })) }))
jest.mock('@/api/productLocation', () => ({
  getProductLocationById: jest.fn(() => Promise.resolve({ data: { code: 1 } })),
  setProductLocation: jest.fn()
}))

const mountForm = (id?: number) => shallowMount(AddProduct, {
  mocks: {
    $route: { query: { id } },
    $router: { push: jest.fn() },
    $message: { success: jest.fn(), error: jest.fn() }
  },
  stubs: ['el-form', 'el-form-item', 'el-input', 'el-input-number', 'el-select', 'el-option', 'el-button', 'router-link']
})

beforeEach(() => jest.clearAllMocks())

it('shows existing stock read only and points edits to stock management', async () => {
  const wrapper = mountForm(42)
  await wrapper.vm.$nextTick()
  const stockInput = wrapper.findAll('el-input-number-stub').at(0)
  expect(stockInput.attributes('disabled')).toBe('true')
  expect(wrapper.text()).toContain('库存调整')
  expect(wrapper.find('router-link-stub').attributes('to')).toBe('/stock')
  wrapper.destroy()
})

it('omits stale stock from product edits while retaining initial stock on creation', () => {
  for (const id of [42, undefined]) {
    const wrapper = mountForm(id)
    const vm: any = wrapper.vm
    vm.ruleForm = { id, name: '商品', image: 'item.png', price: '1', stock: 10 }
    vm.$refs.ruleForm.validate = (callback: (valid: boolean) => void) => callback(true)
    vm.submitForm('ruleForm', null)
    if (id) {
      expect(editProduct).toHaveBeenCalled()
      expect((editProduct as jest.Mock).mock.calls[0][0]).not.toHaveProperty('stock')
    } else {
      expect(addProduct).toHaveBeenCalledWith(expect.objectContaining({ stock: 10 }))
      expect(wrapper.findAll('el-input-number-stub').at(0).attributes('disabled')).toBeUndefined()
    }
    wrapper.destroy()
  }
})
