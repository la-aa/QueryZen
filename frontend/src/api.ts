import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export interface ConnectionView {
  id: string
  name: string
  dbType: string
  schema: string
  maxRows: number
}

export interface Column {
  name: string
  jdbcType: string
}

export interface QueryResult {
  columns: Column[]
  rows: unknown[][]
  rowCount: number
  truncated: boolean
  finalSql: string
  elapsedMs: number
}

export interface VerifyResult {
  intact: boolean
  total: number
  checked: number
  message: string
}

export interface AuditRecord {
  username: string
  ip: string
  sqlText: string
  rowsReturned: number
  elapsedMs: number
  errorMsg: string | null
  ts: string
}

export interface UserView {
  username: string
  roles: string[]
  createdBy: string | null
  createdAt: string | null
}

export interface LoginResult {
  token: string
  username: string
  roles: string[]
  passwordExpired: boolean
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export function login(username: string, password: string) {
  return api.post<LoginResult>('/auth/login', { username, password }).then((r) => r.data)
}

export function changePassword(oldPassword: string, newPassword: string) {
  return api.post<LoginResult>('/auth/change-password', { oldPassword, newPassword }).then((r) => r.data)
}

export function resetUserPassword(username: string, newPassword: string) {
  return api.post<{ result: string; message: string }>('/auth/users/reset-password', { username, newPassword }).then((r) => r.data)
}

export function listUsers() {
  return api.get<UserView[]>('/auth/users').then((r) => r.data)
}

export function createUser(username: string, password: string, roles: string[]) {
  return api.post<UserView>('/auth/users', { username, password, roles }).then((r) => r.data)
}

export interface TableInfo {
  owner: string
  tableName: string
}

export function listConnections() {
  return api.get<ConnectionView[]>('/connections').then((r) => r.data)
}

export function listTables(connectionId: string) {
  return api.get<TableInfo[]>(`/connections/${connectionId}/tables`).then((r) => r.data)
}

export function runQuery(connectionId: string, sql: string) {
  return api.post<QueryResult>('/query', { connectionId, sql }).then((r) => r.data)
}

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

export async function exportXlsx(connectionId: string, sql: string): Promise<void> {
  const res = await api.post<Blob>('/query/export', { connectionId, sql }, { responseType: 'blob' })
  const blob = res.data as Blob
  if (blob.type !== XLSX_MIME) {
    const text = await blob.text()
    let msg = text
    try { msg = JSON.parse(text).error ?? text } catch { /* keep raw text */ }
    throw new Error(Array.isArray(msg) ? text : msg || '导出失败')
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `queryzen-${new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-')}.xlsx`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function verifyAudit() {
  return api.get<VerifyResult>('/audit/verify').then((r) => r.data)
}

export function auditEntries(limit = 50) {
  return api.get<AuditRecord[]>('/audit/entries', { params: { limit } }).then((r) => r.data)
}

export function apiErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { error?: string } | undefined
    return data?.error ?? err.message
  }
  return err instanceof Error ? err.message : String(err)
}