const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')

const source = fs.readFileSync(require('node:path').join(__dirname, '../utils/request.js'), 'utf8')
  .replace(/^import .*$/gm, '')
  .replace('export function request', 'function request') + '\nmodule.exports = { request }'

async function run(response) {
  const commits = []
  const removals = []
  const switches = []
  const logins = []
  const context = {
    module: { exports: {} }, exports: {}, baseUrl: '',
    getApp: () => ({ autoLogin: () => { logins.push('login'); return Promise.resolve(true) } }),
    store: { state: { token: 'old-token' }, commit: (...args) => commits.push(args) },
    uni: {
      request: options => options.success(response),
      removeStorageSync: key => removals.push(key),
      showModal: options => options.success({ confirm: true }),
      switchTab: options => switches.push(options.url)
    }, Promise
  }
  vm.runInNewContext(source, context)
  await context.module.exports.request({ url: '/protected' }).catch(() => {})
  await Promise.resolve()
  assert.deepEqual(commits.filter(x => x[0] === 'setToken'), [['setToken', '']])
  assert.deepEqual(removals, ['token'])
  assert.deepEqual(logins, ['login'])
  assert.deepEqual(switches, ['/pages/home/index'])
}

async function loginFailureDoesNotPromptAgain() {
  const prompts = []
  const context = {
    module: { exports: {} }, exports: {}, baseUrl: '', Promise,
    store: { state: { token: '' }, commit: () => {} },
    uni: {
      request: options => options.success({ statusCode: 401, data: { code: 'AUTHENTICATION_ERROR' } }),
      showModal: options => prompts.push(options)
    }
  }
  vm.runInNewContext(source, context)
  await context.module.exports.request({ url: '/user/user/login' }).catch(() => {})
  assert.equal(prompts.length, 0)
}

function concurrentRequests() {
  const requests = [], prompts = [], removals = [], logins = []
  const store = {
    state: { token: 'old-token' },
    commit: (name, value) => { if (name === 'setToken') store.state.token = value }
  }
  let finishLogin
  const context = {
    module: { exports: {} }, exports: {}, baseUrl: '', Promise, store,
    getApp: () => ({ autoLogin: () => {
      logins.push('login')
      return new Promise(resolve => { finishLogin = () => { store.state.token = 'new-token'; resolve(true) } })
    } }),
    uni: {
      request: options => requests.push(options),
      removeStorageSync: key => removals.push(key),
      showModal: options => prompts.push(options),
      switchTab: () => {}
    }
  }
  vm.runInNewContext(source, context)
  const start = () => context.module.exports.request({ url: '/protected' }).catch(() => {})
  const expire = index => requests[index].success({ statusCode: 401, data: { code: 'AUTHENTICATION_ERROR' } })
  return { start, expire, prompts, removals, logins, store, finish: () => finishLogin() }
}

async function lateUnauthorizedResponsePreservesRestoredToken() {
  const test = concurrentRequests()
  const first = test.start(), second = test.start()
  test.expire(0)
  test.prompts[0].success({ confirm: true })
  test.finish()
  // Let recovery finish completely before the older request returns.
  await new Promise(setImmediate)
  test.expire(1)
  await Promise.all([first, second])
  assert.equal(test.store.state.token, 'new-token', 'late 401 must preserve restored token')
  assert.equal(test.removals.length, 1)
  assert.equal(test.prompts.length, 1)
  assert.equal(test.logins.length, 1)
  const fresh = test.start()
  test.expire(2)
  assert.equal(test.prompts.length, 2, 'a later expired session can recover again')
  test.prompts[1].success({ confirm: false })
  await fresh
}

async function concurrentUnauthorizedResponsesShareRecoveryPrompt() {
  const test = concurrentRequests()
  const first = test.start(), second = test.start()
  test.expire(0)
  // Another request can start after invalidation, while the modal is open.
  const third = test.start()
  test.expire(1)
  test.expire(2)
  assert.equal(test.prompts.length, 1, 'concurrent 401 responses share one modal')
  test.prompts[0].success({ confirm: true })
  const fourth = test.start()
  test.expire(3)
  assert.equal(test.prompts.length, 1, 'login in progress shares the recovery modal')
  test.finish()
  await Promise.all([first, second, third, fourth])
  assert.equal(test.logins.length, 1)
  assert.equal(test.removals.length, 1)
}

async function cancelledRecoveryCanPromptOnNextRequest() {
  const test = concurrentRequests()
  const first = test.start()
  test.expire(0)
  test.prompts[0].success({ confirm: false })
  const second = test.start()
  test.expire(1)
  assert.equal(test.prompts.length, 2)
  test.prompts[1].success({ confirm: false })
  await Promise.all([first, second])
}

Promise.resolve()
  .then(() => run({ statusCode: 401, data: { code: 'SYSTEM_ERROR' } }))
  .then(() => run({ statusCode: 400, data: { code: 'AUTHENTICATION_ERROR' } }))
  .then(loginFailureDoesNotPromptAgain)
  .then(lateUnauthorizedResponsePreservesRestoredToken)
  .then(concurrentUnauthorizedResponsesShareRecoveryPrompt)
  .then(cancelledRecoveryCanPromptOnNextRequest)
  .then(() => console.log('request auth contract: PASS'))
