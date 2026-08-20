import { useCallback, useEffect, useState } from 'react'
import Editor from '@monaco-editor/react'
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

interface Session {
  token: string
  username: string
  roles: string[]
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
  const [result, setResult] = useState<QueryResult | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
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

  const columnDefs: ColDef[] = (result?.columns ?? []).map((c) => ({ field: c.name, headerName: `${c.name} (${c.jdbcType})` }))
  const rowData = result?.rows.map((r) => {
    const obj: Record<string, unknown> = {}
    result.columns.forEach((c, i) => { obj[c.name] = r[i] })
    return obj
  }) ?? []

  async function run() {
    setBusy(true)
    setError('')
    setResult(null)
    try {
      const res = await runQuery(connectionId, sql)
      setResult(res)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function exportResult() {
    setExporting(true)
    setError('')
    try {
      await exportXlsx(connectionId, sql)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  function insertTable(t: TableInfo) {
    setSql(`SELECT * FROM ${t.owner}.${t.tableName} ORDER BY 1`)
    setResult(null)
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
          <button className="export-btn" onClick={exportResult} disabled={exporting || !result || result.rowCount === 0}>
            {exporting ? '导出中...' : '导出 XLSX'}
          </button>
        </div>
        <div className="sql-editor">
          <Editor
            height="200px"
            defaultLanguage="sql"
            theme="vs-dark"
            value={sql}
            onChange={(v) => setSql(v ?? '')}
            options={{ minimap: { enabled: false }, fontSize: 14 }}
          />
        </div>
        {error && <div className="error">{error}</div>}
        {result && (
          <div className="result">
            <div className="result-meta">
              返回 {result.rowCount} 行，耗时 {result.elapsedMs} ms{result.truncated ? '（结果已被截断）' : ''}
              <span className="sql-detail">{result.finalSql}</span>
            </div>
            <div className="ag-theme-quartz grid">
              <AgGridReact rowData={rowData} columnDefs={columnDefs} onGridReady={(p) => p.api.sizeColumnsToFit()} />
            </div>
          </div>
        )}
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