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

Promise.resolve()
  .then(() => run({ statusCode: 401, data: { code: 'SYSTEM_ERROR' } }))
  .then(() => run({ statusCode: 400, data: { code: 'AUTHENTICATION_ERROR' } }))
  .then(loginFailureDoesNotPromptAgain)
  .then(() => console.log('request auth contract: PASS'))
