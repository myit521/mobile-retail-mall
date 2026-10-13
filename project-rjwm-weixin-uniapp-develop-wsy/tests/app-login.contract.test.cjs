const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')

const appSource = fs.readFileSync(require('node:path').join(__dirname, '../App.vue'), 'utf8')
const script = appSource.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*$/gm, '')
  .replace('export default', 'module.exports =')

let finishLogin
let loginCalls = 0
const commits = []
const saved = []
const context = {
  module: { exports: {} }, exports: {}, Promise, console,
  store: { commit: (...args) => commits.push(args) },
  userLogin: () => {
    loginCalls++
    return new Promise(resolve => { finishLogin = resolve })
  },
  uni: {
    login: ({ success }) => success({ code: 'wx-code' }),
    setStorageSync: (...args) => saved.push(args)
  }
}
vm.runInNewContext(script, context)
const app = context.module.exports.methods

async function run() {
  const first = app.autoLogin()
  const second = app.autoLogin()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(loginCalls, 1, 'concurrent recovery should share the login attempt')
  finishLogin({ code: 1, data: { token: 'new-token' } })
  assert.equal(await first, true)
  assert.equal(await second, true)
  assert.deepEqual(commits, [['setToken', 'new-token']])
  assert.deepEqual(saved, [['token', 'new-token']])
  const retry = app.autoLogin()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(loginCalls, 2, 'a later 401 can start a fresh login')
  finishLogin({ code: 1, data: { token: 'later-token' } })
  assert.equal(await retry, true)
  assert.deepEqual(commits[1], ['setToken', 'later-token'])
  console.log('app login contract: PASS')
}

run().catch(error => { console.error(error); process.exitCode = 1 })
