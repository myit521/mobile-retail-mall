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
    Status: {}, OrderDetail: {}, StoreInfo: {}, OrderInfo: {}, ...dependencies,
  }
  vm.runInNewContext(source, context)
  const definition = context.module.exports
  return { ...definition.data(), ...definition.methods }
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
