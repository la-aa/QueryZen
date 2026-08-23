import { useCallback, useEffect, useRef, useState } from 'react'
import Editor, { type OnMount } from '@monaco-editor/react'
import { AgGridReact } from 'ag-grid-react'
import { ModuleRegistry, ClientSideRowModelModule } from 'ag-grid-community'
import type { ColDef } from 'ag-grid-community'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import {
  apiErrorMessage,
  auditEntries,
  exportXlsx,
  listConnections,
  listTables,
  login,
  runQuery,
  verifyAudit,
  type AuditRecord,
  type ConnectionView,
  type QueryResult,
  type TableInfo,
  type VerifyResult,
} from './api'

ModuleRegistry.registerModules([ClientSideRowModelModule])

function splitStatements(sql: string): string[] {
  const out: string[] = []
  let current = ''
  let quote: string | null = null
  let lineComment = false
  let blockComment = false
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]
    const next = sql[i + 1]
    if (lineComment) {
      current += ch
      if (ch === '\n') lineComment = false
      continue
    }
    if (blockComment) {
      current += ch
      if (ch === '*' && next === '/') { current += '/'; i++; blockComment = false }
      continue
    }
    if (quote) {
      current += ch
      if (ch === quote && sql[i - 1] !== '\\') quote = null
      continue
    }
    if (ch === '-' && next === '-') { lineComment = true; current += ch; continue }
    if (ch === '/' && next === '*') { blockComment = true; current += ch; continue }
    if (ch === "'" || ch === '"' || ch === '`') { quote = ch; current += ch; continue }
    if (ch === ';') { out.push(current); current = ''; continue }
    current += ch
  }
  if (current.trim()) out.push(current)
  return out.map((s) => s.trim()).filter(Boolean)
}

function scopeSql(editor: Parameters<OnMount>[0] | null, fallback: string): string {
  if (!editor) return fallback
  const sel = editor.getSelection()
  const model = editor.getModel()
  if (!sel || sel.isEmpty() || !model) return fallback
  const text = model.getValueInRange(sel)
  return text.trim() ? text : fallback
}

interface Session {
  token: string
  username: string
  roles: string[]
}

interface ResultCard {
  key: string
  sql: string
  status: 'loading' | 'ok' | 'error'
  result?: QueryResult
  error?: string
}

function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const t = localStorage.getItem('token')
    const u = localStorage.getItem('username')
    return t && u ? { token: t, username: u, roles: [] } : null
  })

  if (!session) {
    return <LoginView onLogin={setSession} />
  }
  return <MainView session={session} onLogout={() => {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    setSession(null)
  }} />
}

function LoginView({ onLogin }: { onLogin: (s: Session) => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const s = await login(username, password)
      localStorage.setItem('token', s.token)
      localStorage.setItem('username', s.username)
      onLogin({ token: s.token, username: s.username, roles: s.roles })
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-box" onSubmit={submit}>
        <h1>QueryZen</h1>
        <p className="sub">只读数据库查询工具</p>
        <label>
          用户名
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          密码
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="error">{error}</div>}
        <button disabled={busy}>{busy ? '登录中...' : '登录'}</button>
      </form>
    </div>
  )
}

function MainView({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const [tab, setTab] = useState<'query' | 'audit'>('query')

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">QueryZen</div>
        <div className="spacer" />
        <span className="user">{session.username}</span>
        <button className="ghost" onClick={onLogout}>退出</button>
      </header>
      <nav className="tabs">
        <button className={tab === 'query' ? 'active' : ''} onClick={() => setTab('query')}>查询</button>
        <button className={tab === 'audit' ? 'active' : ''} onClick={() => setTab('audit')}>审计日志</button>
      </nav>
      {tab === 'query' ? <QueryPanel /> : <AuditPanel />}
    </div>
  )
}

function QueryPanel() {
  const [connections, setConnections] = useState<ConnectionView[]>([])
  const [connectionId, setConnectionId] = useState('')
  const [tables, setTables] = useState<TableInfo[]>([])
  const [tablesError, setTablesError] = useState('')
  const [sql, setSql] = useState('SELECT * FROM demo.employees ORDER BY id')
  const [cards, setCards] = useState<ResultCard[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    listConnections()
      .then((cs) => {
        setConnections(cs)
        if (cs.length > 0) setConnectionId(cs[0].id)
      })
      .catch((err) => setError(apiErrorMessage(err)))
  }, [])

  useEffect(() => {
    if (!connectionId) return
    setTables([])
    setTablesError('')
    listTables(connectionId)
      .then(setTables)
      .catch((err) => setTablesError(apiErrorMessage(err)))
  }, [connectionId])

  function cardRows(c: ResultCard): Record<string, unknown>[] {
    const res = c.result
    if (!res) return []
    return res.rows.map((r) => {
      const obj: Record<string, unknown> = {}
      res.columns.forEach((col, i) => { obj[col.name] = r[i] })
      return obj
    })
  }

  async function exportCard(c: ResultCard) {
    setExporting(true)
    setError('')
    try {
      await exportXlsx(connectionId, c.sql)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  async function run() {
    const stmts = splitStatements(scopeSql(editorRef.current, sql))
    if (stmts.length === 0) { setError('没有可执行的语句'); return }
    setBusy(true)
    setError('')
    setCards(stmts.map((s, i) => ({ key: `${Date.now()}-${i}`, sql: s, status: 'loading' as const })))
    const settled = await Promise.allSettled(stmts.map((s) => runQuery(connectionId, s)))
    setCards(settled.map((r, i) => {
      if (r.status === 'fulfilled') return { key: `${Date.now()}-${i}`, sql: stmts[i], status: 'ok' as const, result: r.value }
      return { key: `${Date.now()}-${i}`, sql: stmts[i], status: 'error' as const, error: apiErrorMessage(r.reason) }
    }))
    setBusy(false)
  }

  function insertTable(t: TableInfo) {
    setSql(`SELECT * FROM ${t.owner}.${t.tableName} ORDER BY 1`)
    setCards([])
    setError('')
  }

  return (
    <div className="query-body">
      <aside className="sidebar">
        <div className="sidebar-title">
          表清单
          <button className="mini" onClick={() => listTables(connectionId).then(setTables).catch((e) => setTablesError(apiErrorMessage(e)))}>刷新</button>
        </div>
        {tablesError && <div className="error">{tablesError}</div>}
        <ul className="table-list">
          {tables.map((t) => (
            <li key={`${t.owner}.${t.tableName}`} onClick={() => insertTable(t)} title="点击填入查询框">
              {t.tableName}
              <span className="table-owner">{t.owner}</span>
            </li>
          ))}
        </ul>
      </aside>
      <div className="query-main">
        <div className="query-head">
          <select value={connectionId} onChange={(e) => setConnectionId(e.target.value)}>
            {connections.map((c) => (
              <option key={c.id} value={c.id}>{c.name}（{c.dbType}）</option>
            ))}
          </select>
          <button onClick={run} disabled={busy || !connectionId || !sql.trim()}>
            {busy ? '执行中...' : '执行查询'}
          </button>
        </div>
        <div className="sql-editor">
          <Editor
            height="200px"
            defaultLanguage="sql"
            theme="vs-dark"
            value={sql}
            onChange={(v) => setSql(v ?? '')}
            onMount={(editor) => { editorRef.current = editor }}
            options={{ minimap: { enabled: false }, fontSize: 14 }}
          />
        </div>
        <div className="hint">选中部分文本时只执行选中内容；否则执行全文（多条语句以“;”分隔，将并行执行、逐条展示）</div>
        {error && <div className="error">{error}</div>}
        {cards.map((c) => (
          <div className="result" key={c.key}>
            <div className="result-meta">
              <span className="sql-detail">{c.sql}</span>
            </div>
            {c.status === 'loading' && <div className="result-loading">执行中...</div>}
            {c.status === 'error' && <div className="error">{c.error}</div>}
            {c.status === 'ok' && c.result && (
              <>
                <div className="result-meta">
                  返回 {c.result.rowCount} 行，耗时 {c.result.elapsedMs} ms{c.result.truncated ? '（结果已被截断）' : ''} · {c.result.finalSql}
                </div>
                <div className="query-head">
                  <button className="export-btn" onClick={() => exportCard(c)} disabled={exporting || c.result.rowCount === 0}>
                    {exporting ? '导出中...' : '导出 XLSX'}
                  </button>
                </div>
                <div className="ag-theme-quartz grid">
                  <AgGridReact rowData={cardRows(c)} columnDefs={c.result.columns.map((col) => ({ field: col.name, headerName: `${col.name} (${col.jdbcType})` }))} onGridReady={(p) => p.api.sizeColumnsToFit()} />
                </div>
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

function AuditPanel() {
  const [records, setRecords] = useState<AuditRecord[]>([])
  const [verify, setVerify] = useState<VerifyResult | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setRecords(await auditEntries(50))
    } catch (err) {
      setError(apiErrorMessage(err))
    }
  }, [])

  useEffect(() => { load() }, [load])

  const columnDefs: ColDef[] = [
    { field: 'ts', headerName: '时间' },
    { field: 'username', headerName: '用户' },
    { field: 'ip', headerName: 'IP' },
    { field: 'sqlText', headerName: 'SQL', width: 420 },
    { field: 'rowsReturned', headerName: '行数' },
    { field: 'elapsedMs', headerName: '耗时(ms)' },
    { field: 'errorMsg', headerName: '错误' },
  ]

  async function verifyChain() {
    setError('')
    try {
      setVerify(await verifyAudit())
    } catch (err) {
      setError(apiErrorMessage(err))
    }
  }

  return (
    <div className="audit-layout">
      <div className="audit-head">
        <button onClick={load}>刷新</button>
        <button onClick={verifyChain}>校验链完整性</button>
        {verify && (
          <span className={verify.intact ? 'ok' : 'error'}>
            {verify.message}
          </span>
        )}
      </div>
      {error && <div className="error">{error}</div>}
      <div className="ag-theme-quartz grid">
        <AgGridReact rowData={records} columnDefs={columnDefs} />
      </div>
    </div>
  )
}

export default App