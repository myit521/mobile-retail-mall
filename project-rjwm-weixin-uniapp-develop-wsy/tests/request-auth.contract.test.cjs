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
  const context = {
    module: { exports: {} }, exports: {}, baseUrl: '',
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
  assert.deepEqual(commits.filter(x => x[0] === 'setToken'), [['setToken', '']])
  assert.deepEqual(removals, ['token'])
  assert.deepEqual(switches, ['/pages/home/index'])
}

Promise.resolve()
  .then(() => run({ statusCode: 401, data: { code: 'SYSTEM_ERROR' } }))
  .then(() => run({ statusCode: 400, data: { code: 'AUTHENTICATION_ERROR' } }))
  .then(() => console.log('request auth contract: PASS'))
