export class Phoenix {
  private baseUrl: string; private apiKey?: string
  constructor(baseUrl = 'http://localhost:8080', apiKey?: string) { this.baseUrl = baseUrl.replace(/\/$/, ''); this.apiKey = apiKey }
  private async req<T>(method: string, path: string, data?: any): Promise<T> {
    const h: Record<string,string> = {'Content-Type':'application/json'}
    if (this.apiKey) h['Authorization'] = 'Bearer ' + this.apiKey
    const r = await fetch(this.baseUrl + path, {method, headers:h, body: data ? JSON.stringify(data) : undefined})
    if (!r.ok) throw new Error('HTTP ' + r.status); return r.json()
  }
  async chat(content: string): Promise<any> { return this.req('POST', '/api/agent/chat', {content}) }
  async health(): Promise<any> { return this.req('GET', '/api/agent/health') }
}
