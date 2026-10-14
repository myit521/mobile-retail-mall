const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')

function page(file, dependencies = {}) {
  let source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8')
  if (file.endsWith('.vue')) source = source.match(/<script>([\s\S]*?)<\/script>/)[1]
  source = source.replace(/^import\s+[\s\S]*?\s+from\s+['"][^'"]+['"][^\n]*/gm, '')
    .replace('export default', 'module.exports =')
  const context = {
    module: { exports: {} }, mapState: () => ({}), mapMutations: () => ({}),
    navBar: {}, Phone: {}, popMask: {}, popCart: {}, ProductDetail: {},
    Status: {}, OrderDetail: {}, StoreInfo: {}, OrderInfo: {}, Empty: {},
    HeadInfo: {}, OrderList: {}, ...dependencies,
  }
  vm.runInNewContext(source, context)
  const definition = context.module.exports
  return { ...definition, ...definition.data(), ...definition.methods }
}

test('each specification confirmation uses its new selection even if that product is already in the cart', async () => {
  const requests = []
  const instance = page('pages/index/index.js', {
    newAddShoppingCartAdd: params => { requests.push(JSON.parse(JSON.stringify(params))); return Promise.resolve({ code: 1 }) },
    uni: { showToast() {} },
  })
  instance.getTableOrderDishListes = () => {}
  instance.getDishListDataes = () => {}
  instance.rightIdAndType = { index: 0 }
  instance.orderListDataes = [{ productId: 7, specInfo: '颜色:黑色' }]
  const item = { id: 7, specInfo: '颜色:黑色', flavorRemark: '[{"specName":"颜色","specValue":"黑色"}]' }
  for (const color of ['白色', '蓝色']) {
    instance.openMoreNormPop = true
    instance.flavorDataes = [{ specName: '颜色', specValue: color }]
    await instance.addDishAction(item, '普通')
    await Promise.resolve()
  }
  assert.deepEqual(requests, [{ productId: 7, specInfo: '颜色:白色' }, { productId: 7, specInfo: '颜色:蓝色' }])
})

test('cart increment preserves the selected cart row specification', async () => {
  let request
  const instance = page('pages/index/index.js', {
    newAddShoppingCartAdd: params => { request = params; return Promise.resolve({ code: 0 }) },
    uni: { showToast() {} },
  })
  instance.flavorDataes = [{ specName: '颜色', specValue: '白色' }]
  await instance.addDishAction({ productId: 7, specInfo: '颜色:黑色' }, '购物车')
  assert.equal(request.specInfo, '颜色:黑色')
})

test('product decrement uses its only cart specification', async () => {
  let request
  const instance = page('pages/index/index.js', {
    newShoppingCartSub: params => { request = params; return Promise.resolve({ code: 0 }) },
  })
  instance.orderListDataes = [{ productId: 7, specInfo: '颜色:白色', number: 1 }]
  await instance.redDishAction({ id: 7 }, '普通')
  assert.equal(request.specInfo, '颜色:白色')
})

test('product decrement opens the cart when multiple specifications need a choice', async () => {
  const instance = page('pages/index/index.js', {
    newShoppingCartSub: () => { throw new Error('must choose a specific cart row') },
  })
  instance.orderListDataes = [
    { productId: 7, specInfo: '颜色:白色', number: 1 },
    { productId: 7, specInfo: '颜色:黑色', number: 2 },
  ]
  instance.dishListData = [{ id: 7 }]
  instance.dishListItems = []
  instance.setOrderNum()
  assert.equal(instance.dishListData[0].dishNumber, 3)
  await instance.redDishAction({ id: 7 }, '普通')
  assert.equal(instance.openOrderCartList, true)
})

for (const detected of ['iPhone 15 Pro', 'Apple iPhone 15 Pro', 'iPhone 15 Pro device']) {
  test(`matches the full model before a shorter prefix: ${detected}`, () => {
    const instance = page('pages/phoneModel/index.vue')
    instance.phoneModelList = [{ id: 1, modelName: 'iPhone 15', brand: 'Apple' }, { id: 2, modelName: 'iPhone 15 Pro', brand: 'Apple' }]
    instance.normalizedDetectedModel = instance.normalizeModelName(detected)
    let selected
    instance.loadBrandModels = (brand, id) => { selected = id }
    instance.tryMatchDetectedModel()
    assert.equal(instance.matchedPhoneModel.id, 2)
    assert.equal(selected, 2)
  })
}

test('unknown device does not select recommendations', () => {
  const instance = page('pages/phoneModel/index.vue')
  instance.phoneModelList = [{ id: 1, modelName: 'iPhone 15', brand: 'Apple' }]
  instance.normalizedDetectedModel = 'unknown'
  instance.tryMatchDetectedModel()
  assert.equal(instance.matchedPhoneModel, null)
})

for (const suffix of ['Pro', 'Max', 'Plus', 'Pro Max']) {
  test(`does not substitute the base model when the ${suffix} variant is absent`, () => {
    const instance = page('pages/phoneModel/index.vue')
    instance.phoneModelList = [{ id: 1, modelName: 'iPhone 15', brand: 'Apple' }]
    instance.normalizedDetectedModel = instance.normalizeModelName(`iPhone 15 ${suffix}`)
    instance.loadBrandModels = () => { throw new Error('must not load base model') }
    instance.tryMatchDetectedModel()
    assert.equal(instance.matchedPhoneModel, null)
  })
}

test('reorder restores the cart then navigates to the registered shop tab', async () => {
  const actions = []
  const tabs = JSON.parse(fs.readFileSync(path.join(__dirname, '../pages.json'), 'utf8').replace(/\/\/[^\n]*/g, '')).tabBar.list
  const instance = page('pages/details/index.js', {
    delShoppingCart: async () => { actions.push('clear') },
    repetitionOrder: async id => { actions.push(`restore:${id}`); return { code: 1 } },
    uni: {
      switchTab: ({ url }) => { assert.ok(tabs.some(tab => '/' + tab.pagePath === url)); actions.push('shop') },
      redirectTo: () => { actions.push('invalid redirect') },
    },
  })
  await instance.oneMoreOrder(42)
  await Promise.resolve()
  assert.deepEqual(actions, ['clear', 'restore:42', 'shop'])
})

for (const [file, method] of [['pages/my/my.vue', 'oneOrderFun'], ['pages/historyOrder/historyOrder.vue', 'oneMoreOrder']]) {
  test(`${file} reorder switches to the shop tab after restoring the cart`, async () => {
    const actions = []
    const instance = page(file, {
      getOrderPage() {}, getCurrentPages: () => [{ route: 'pages/my/my' }],
      delShoppingCart: async () => { actions.push('clear') },
      repetitionOrder: async id => { actions.push(`restore:${id}`); return { code: 1 } },
      uni: {
        switchTab: ({ url }) => { actions.push(url) },
        navigateBack: () => { actions.push('back') },
      },
    })
    await instance[method](42)
    await Promise.resolve()
    assert.deepEqual(actions, ['clear', 'restore:42', '/pages/index/index'])
  })
}

function historyPage() {
  const pending = []
  const instance = page('pages/historyOrder/historyOrder.vue', {
    getOrderPage: params => new Promise(resolve => { pending.push({ params, resolve }) }),
    setTimeout: callback => callback(),
    uni: { showLoading() {}, hideLoading() {}, stopPullDownRefresh() {} },
  })
  async function complete(index, id, total = 30) {
    pending[index].resolve({ code: 1, data: { records: [{ id, orderDetailList: [] }], total } })
    for (let i = 0; i < 5; i++) await Promise.resolve()
  }
  return { instance, pending, complete }
}

test('history ignores an old filter response including after switching back to the same filter', async () => {
  const { instance, complete } = historyPage()
  instance.getList()
  instance.changeTab(1)
  instance.changeTab(0)
  await complete(2, 'current', 7)
  await complete(1, 'other', 99)
  await complete(0, 'old', 88)
  assert.deepEqual(Array.from(instance.recentOrdersList, row => row.id), ['current'])
  assert.equal(instance.pageInfo.total, 7)
})

test('history refresh ignores an older response for the same filter', async () => {
  const { instance, complete } = historyPage()
  instance.getList()
  instance.onPullDownRefresh()
  await complete(1, 'fresh', 5)
  await complete(0, 'old', 99)
  assert.deepEqual(Array.from(instance.recentOrdersList, row => row.id), ['fresh'])
  assert.equal(instance.pageInfo.total, 5)
})

test('history serializes pagination without skipping or dropping legitimate pages', async () => {
  const { instance, pending, complete } = historyPage()
  instance.getList()
  await complete(0, 'first')
  instance.lower()
  instance.onReachBottom()
  instance.lower()
  assert.equal(pending.length, 2)
  assert.equal(instance.pageInfo.page, 2)
  await complete(1, 'second')
  instance.onReachBottom()
  assert.equal(pending[2].params.page, 3)
  await complete(2, 'third')
  assert.deepEqual(Array.from(instance.recentOrdersList, row => row.id), ['first', 'second', 'third'])
})

test('history refresh rejects a pending previous page and waits for the new first page', async () => {
  const { instance, pending, complete } = historyPage()
  instance.getList()
  await complete(0, 'first')
  instance.lower()
  instance.onPullDownRefresh()
  instance.lower()
  assert.equal(pending.length, 3)
  await complete(1, 'old second', 99)
  assert.equal(instance.recentOrdersList.length, 0)
  assert.equal(instance.pageInfo.total, 0)
  instance.onReachBottom()
  instance.lower()
  assert.equal(pending.length, 3)
  await complete(2, 'fresh', 14)
  instance.lower()
  assert.equal(pending[3].params.page, 2)
  await complete(3, 'fresh second', 14)
  assert.deepEqual(Array.from(instance.recentOrdersList, row => row.id), ['fresh', 'fresh second'])
})
