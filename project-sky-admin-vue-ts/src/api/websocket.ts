import request from '@/utils/request'

export const issueWebSocketTicket = () => request({
  url: '/notifications/websocket-ticket',
  method: 'post'
})

export const buildWebSocketUrl = (baseUrl: string, ticket: string) =>
  `${baseUrl.replace(/\/+$/, '')}?ticket=${encodeURIComponent(ticket)}`

export class WebSocketAttemptGuard {
  private generation = 0
  private disposed = false

  public begin() {
    return ++this.generation
  }

  public canOpen(generation: number) {
    return !this.disposed && generation === this.generation
  }

  public dispose() {
    this.disposed = true
    this.generation++
  }
}
