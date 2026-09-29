import { buildWebSocketUrl, WebSocketAttemptGuard } from '@/api/websocket'

describe('admin websocket contract', () => {
  it('puts only the short-lived ticket in the websocket URL', () => {
    expect(buildWebSocketUrl('ws://localhost:8080/ws/', 'ticket+/=')).toBe(
      'ws://localhost:8080/ws?ticket=ticket%2B%2F%3D'
    )
  })

  it('invalidates an awaited connection attempt when the component is disposed', () => {
    const guard = new WebSocketAttemptGuard()
    const pending = guard.begin()
    guard.dispose()
    expect(guard.canOpen(pending)).toBe(false)
    expect(guard.canOpen(guard.begin())).toBe(false)
  })
})
