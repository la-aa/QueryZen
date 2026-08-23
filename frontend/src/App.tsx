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
  changePassword,
  createUser,
  exportXlsx,
  listConnections,
  listTables,
  listUsers,
  login,
  resetUserPassword,
  runQuery,
  verifyAudit,
  type AuditRecord,
  type ConnectionView,
  type LoginResult,
  type QueryResult,
  type TableInfo,
  type UserView,
  type VerifyResult,
} from './api'

import {
  AlertIcon,
  AuditIcon,
  ClockIcon,
  DatabaseIcon,
  DownloadIcon,
  EyeIcon,
  EyeOffIcon,
  KeyIcon,
  LockIcon,
  LogoIcon,
  LogoutIcon,
  PlayIcon,
  RefreshIcon,
  ShieldCheckIcon,
  TableIcon,
  UserIcon,
  UsersIcon,
} from './icons'

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

interface QueryTab {
  id: string
  title: string
  sql: string
  cards: ResultCard[]
  error: string
}

function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const t = localStorage.getItem('token')
    const u = localStorage.getItem('username')
    const r = localStorage.getItem('roles')
    const roles: string[] = r ? JSON.parse(r) : []
    return t && u ? { token: t, username: u, roles } : null
  })
  const [mustChange, setMustChange] = useState(() => localStorage.getItem('passwordExpired') === '1')

  const applySession = (s: LoginResult) => {
    localStorage.setItem('token', s.token)
    localStorage.setItem('username', s.username)
    localStorage.setItem('roles', JSON.stringify(s.roles))
    localStorage.setItem('passwordExpired', s.passwordExpired ? '1' : '0')
    setSession({ token: s.token, username: s.username, roles: s.roles })
    setMustChange(s.passwordExpired)
  }

  const clearSession = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('roles')
    localStorage.removeItem('passwordExpired')
    setSession(null)
    setMustChange(false)
  }

  if (!session) {
    return <LoginView onLogin={applySession} />
  }
  return <MainView session={session} mustChange={mustChange} onLogout={clearSession} onPasswordChanged={applySession} />
}

function LoginView({ onLogin }: { onLogin: (s: LoginResult) => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin')
  const [showPwd, setShowPwd] = useState(false)
  const [showForgot, setShowForgot] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const s = await login(username, password)
      onLogin(s)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-box" onSubmit={submit}>
        <div className="login-logo"><LogoIcon size={40} /></div>
        <h1>QueryZen</h1>
        <p className="sub">只读数据库查询工具</p>
        <label>
          <span className="field-label"><UserIcon /> 用户名</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          <span className="field-label"><KeyIcon /> 密码</span>
          <span className="pwd-wrap">
            <input
              type={showPwd ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button type="button" className="pwd-toggle" onClick={() => setShowPwd((v) => !v)} title={showPwd ? '隐藏密码' : '显示密码'}>
              {showPwd ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </span>
        </label>
        <div className="login-links">
          <button type="button" className="link" onClick={() => setShowForgot((v) => !v)}>
            忘记密码？
          </button>
        </div>
        {showForgot && (
          <div className="error forgot-hint">
            <AlertIcon size={14} /> 请通过账号管理员重置密码：管理员在「用户管理」中为你重置后，
            你将收到一个临时密码；用临时密码登录后系统会强制要求更新密码。
          </div>
        )}
        {error && <div className="error">{error}</div>}
        <button className="login-btn" disabled={busy}>{busy ? '登录中...' : '登 录'}</button>
      </form>
    </div>
  )
}

function MainView({ session, mustChange, onLogout, onPasswordChanged }:
  { session: Session; mustChange: boolean; onLogout: () => void; onPasswordChanged: (s: LoginResult) => void }) {
  const isAdmin = session.roles.includes('admin')
  const [tab, setTab] = useState<'query' | 'audit' | 'users'>('query')

  if (mustChange) {
    return <ForceChangeView session={session} onLogout={onLogout} onChanged={onPasswordChanged} />
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><LogoIcon size={20} /> QueryZen</div>
        <span className="role-tag"><ShieldCheckIcon size={13} /> {session.roles.join(', ') || 'user'}</span>
        <div className="spacer" />
        <span className="user"><UserIcon size={14} /> {session.username}</span>
        <button className="ghost" onClick={onLogout}><LogoutIcon size={14} /> 退出</button>
      </header>
      <nav className="tabs">
        <button className={tab === 'query' ? 'active' : ''} onClick={() => setTab('query')}>
          <DatabaseIcon size={15} /> 查询
        </button>
        <button className={tab === 'audit' ? 'active' : ''} onClick={() => setTab('audit')}>
          <AuditIcon size={15} /> 审计日志
        </button>
        {isAdmin && (
          <button className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>
            <UsersIcon size={15} /> 用户管理
          </button>
        )}
      </nav>
      {tab === 'query' && <QueryPanel />}
      {tab === 'audit' && <AuditPanel />}
      {tab === 'users' && <UserPanel />}
    </div>
  )
}

function ForceChangeView({ session, onLogout, onChanged }: {
  session: Session; onLogout: () => void; onChanged: (s: LoginResult) => void }) {
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (newPassword !== confirm) { setError('两次输入的新密码不一致'); return }
    setBusy(true)
    setError('')
    try {
      const s = await changePassword(oldPassword, newPassword)
      onChanged(s)
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-box" onSubmit={submit}>
        <div className="login-logo"><LockIcon size={40} /></div>
        <h1>密码已过期</h1>
        <p className="sub">为确保账号安全，请先更新密码（当前用户：{session.username}）</p>
        <label>
          <span className="field-label"><KeyIcon /> 原密码</span>
          <input type="password" value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} autoFocus required />
        </label>
        <label>
          <span className="field-label"><LockIcon /> 新密码（至少 6 位）</span>
          <span className="pwd-wrap">
            <input
              type={showPwd ? 'text' : 'password'}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
            <button type="button" className="pwd-toggle" onClick={() => setShowPwd((v) => !v)} title={showPwd ? '隐藏密码' : '显示密码'}>
              {showPwd ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </span>
        </label>
        <label>
          <span className="field-label"><ShieldCheckIcon /> 确认新密码</span>
          <input type={showPwd ? 'text' : 'password'} value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </label>
        {error && <div className="error">{error}</div>}
        <button className="login-btn" disabled={busy}>{busy ? '提交中...' : '更新密码'}</button>
        <div className="login-links">
          <button type="button" className="link" onClick={onLogout}><LogoutIcon size={13} /> 退出登录</button>
        </div>
      </form>
    </div>
  )
}

function QueryPanel() {
  const [connections, setConnections] = useState<ConnectionView[]>([])
  const [connectionId, setConnectionId] = useState('')
  const [tables, setTables] = useState<TableInfo[]>([])
  const [tablesError, setTablesError] = useState('')
  const [tabs, setTabs] = useState<QueryTab[]>([
    { id: 'tab-1', title: '查询 1', sql: 'SELECT * FROM demo.employees ORDER BY id', cards: [], error: '' },
  ])
  const [activeId, setActiveId] = useState('tab-1')
  const [busy, setBusy] = useState(false)
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const [exporting, setExporting] = useState(false)

  const activeTab = tabs.find((t) => t.id === activeId) ?? tabs[0]

  function updateTab(id: string, patch: Partial<QueryTab>) {
    setTabs((prev) => prev.map((t) => (t.id === id ? { ...t, ...patch } : t)))
  }

  function addTab() {
    const id = `tab-${Date.now()}`
    setTabs((prev) => [...prev, { id, title: `查询 ${prev.length + 1}`, sql: '', cards: [], error: '' }])
    setActiveId(id)
  }

  function closeTab(id: string) {
    if (tabs.length <= 1) return
    const idx = tabs.findIndex((t) => t.id === id)
    const next = tabs.filter((t) => t.id !== id)
    setTabs(next)
    if (activeId === id) setActiveId(next[Math.min(idx, next.length - 1)].id)
  }

  useEffect(() => {
    listConnections()
      .then((cs) => {
        setConnections(cs)
        if (cs.length > 0) setConnectionId(cs[0].id)
      })
      .catch((err) => updateTab(activeId, { error: apiErrorMessage(err) }))
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
    try {
      await exportXlsx(connectionId, c.sql)
    } catch (err) {
      updateTab(activeId, { error: apiErrorMessage(err) })
    } finally {
      setExporting(false)
    }
  }

  async function run() {
    if (!activeTab) return
    const stmts = splitStatements(scopeSql(editorRef.current, activeTab.sql))
    if (stmts.length === 0) { updateTab(activeId, { error: '没有可执行的语句' }); return }
    setBusy(true)
    updateTab(activeId, {
      error: '',
      cards: stmts.map((s, i) => ({ key: `${Date.now()}-${i}`, sql: s, status: 'loading' as const })),
    })
    const settled = await Promise.allSettled(stmts.map((s) => runQuery(connectionId, s)))
    updateTab(activeId, {
      cards: settled.map((r, i) => {
        if (r.status === 'fulfilled') return { key: `${Date.now()}-${i}`, sql: stmts[i], status: 'ok' as const, result: r.value }
        return { key: `${Date.now()}-${i}`, sql: stmts[i], status: 'error' as const, error: apiErrorMessage(r.reason) }
      }),
    })
    setBusy(false)
  }

  function insertTable(t: TableInfo) {
    if (!activeTab) return
    updateTab(activeId, { sql: `SELECT * FROM ${t.owner}.${t.tableName} ORDER BY 1`, cards: [], error: '' })
  }

  return (
    <div className="query-body">
      <aside className="sidebar">
        <div className="sidebar-title">
          <span><TableIcon size={14} /> 表清单</span>
          <button className="mini" onClick={() => listTables(connectionId).then(setTables).catch((e) => setTablesError(apiErrorMessage(e)))}><RefreshIcon size={13} /> 刷新</button>
        </div>
        {tablesError && <div className="error">{tablesError}</div>}
        {!connectionId && <div className="empty-state small">请先选择连接</div>}
        {connectionId && !tablesError && tables.length === 0 && (
          <div className="empty-state small">当前连接没有可见表</div>
        )}
        <ul className="table-list">
          {tables.map((t) => (
            <li key={`${t.owner}.${t.tableName}`} onClick={() => insertTable(t)} title="点击填入下表名，自动生成 SELECT 查询">
              <TableIcon size={13} /> {t.tableName}
              <span className="table-owner">{t.owner}</span>
            </li>
          ))}
        </ul>
      </aside>
      <div className="query-main">
        <div className="query-head">
          <label className="conn-select">
            <span className="field-label"><DatabaseIcon /> 数据库连接</span>
            <select value={connectionId} onChange={(e) => setConnectionId(e.target.value)}>
              {connections.map((c) => (
                <option key={c.id} value={c.id}>{c.name}（{c.dbType}）</option>
              ))}
            </select>
          </label>
          <button className="primary-btn" onClick={run} disabled={busy || !connectionId || !activeTab?.sql.trim()}>
            <PlayIcon size={14} /> {busy ? '执行中...' : '执行查询'}
          </button>
        </div>
        <div className="query-tabs">
          {tabs.map((t) => (
            <div key={t.id} className={`qtab ${t.id === activeId ? 'active' : ''}`} onClick={() => setActiveId(t.id)}>
              <span className="qtab-title">{t.title}</span>
              {tabs.length > 1 && (
                <button
                  className="qtab-close"
                  title="关闭该查询"
                  onClick={(e) => { e.stopPropagation(); closeTab(t.id) }}
                >×</button>
              )}
            </div>
          ))}
          <button className="qtab-add" title="新建查询" onClick={addTab}>+ 新建查询</button>
        </div>
        <div className="sql-editor">
          <Editor
            height="200px"
            defaultLanguage="sql"
            theme="vs-dark"
            value={activeTab?.sql ?? ''}
            onChange={(v) => activeTab && updateTab(activeTab.id, { sql: v ?? '' })}
            onMount={(editor) => { editorRef.current = editor }}
            options={{ minimap: { enabled: false }, fontSize: 14 }}
          />
        </div>
        <div className="hint">
          <AlertIcon size={13} /> 选中部分文本时只执行选中内容；未选中则执行全文。多条语句以“;”分隔，将并行执行、逐条展示。
        </div>
        {activeTab?.error && <div className="error">{activeTab.error}</div>}
        {!busy && activeTab && activeTab.cards.length === 0 && !activeTab.error && (
          <div className="empty-state">
            <DatabaseIcon size={32} />
            <p>还没有查询结果</p>
            <span className="empty-desc">在上方输入只读 SQL 并点击「执行查询」，结果将在这里分条展示</span>
          </div>
        )}
        {activeTab?.cards.map((c) => (
          <div className="result" key={c.key}>
            <div className="result-head">
              <span className="sql-detail">{c.sql}</span>
              {c.status === 'loading' && <span className="result-loading"><RefreshIcon size={13} /> 执行中...</span>}
              {c.status === 'error' && <span className="result-badge error-badge"><AlertIcon size={13} /> 失败</span>}
              {c.status === 'ok' && <span className="result-badge ok-badge"><ShieldCheckIcon size={13} /> 已完成</span>}
            </div>
            {c.status === 'error' && <div className="error">{c.error}</div>}
            {c.status === 'ok' && c.result && (
              <>
                <div className="result-meta">
                  <span><TableIcon size={13} /> 返回 {c.result.rowCount} 行</span>
                  <span><ClockIcon size={13} /> 耗时 {c.result.elapsedMs} ms</span>
                  {c.result.truncated && <span className="truncated"><AlertIcon size={13} /> 结果已被截断（仅前 {c.result.rowCount} 行）</span>}
                </div>
                <div className="query-head">
                  <button className="export-btn" onClick={() => exportCard(c)} disabled={exporting || c.result.rowCount === 0}>
                    <DownloadIcon size={14} /> {exporting ? '导出中...' : '导出 XLSX'}
                  </button>
                  <span className="sql-detail">实际执行 SQL：{c.result.finalSql}</span>
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

function ResetRenderer(props: { data: UserView }) {
  return (
    <button className="mini reset-btn" onClick={async () => {
      const np = window.prompt(`为 ${props.data.username} 设置新的临时密码（至少 6 位）`)
      if (!np || np.length < 6) { window.alert('请填写至少 6 位的临时密码'); return }
      try {
        const r = await resetUserPassword(props.data.username, np)
        window.alert(r.message)
      } catch (err) {
        window.alert(apiErrorMessage(err))
      }
    }}><KeyIcon size={13} /> 重置密码</button>
  )
}

function UserPanel() {
  const [users, setUsers] = useState<UserView[]>([])
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [isAdmin, setIsAdmin] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setUsers(await listUsers())
    } catch (err) {
      setError(apiErrorMessage(err))
    }
  }, [])

  useEffect(() => { load() }, [load])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (busy) return
    if (password !== confirm) { setError('两次输入的密码不一致'); return }
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await createUser(username.trim(), password, isAdmin ? ['admin'] : ['user'])
      setMessage(`账号 ${username.trim()} 创建成功`)
      setUsername('')
      setPassword('')
      setConfirm('')
      setIsAdmin(false)
      await load()
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="audit-layout">
      <div className="user-form">
        <div className="panel-head">
          <div>
            <h2><UsersIcon size={16} /> 创建账号</h2>
            <p className="panel-desc">仅管理员可用：新账号密码有效期 30 天，过期后首次登录需强制更新</p>
          </div>
          <button className="ghost-inline" onClick={load}><RefreshIcon size={13} /> 刷新</button>
        </div>
        <form onSubmit={submit} className="user-form-row">
          <label>
            <span className="field-label"><UserIcon /> 用户名</span>
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="3-64 位字母/数字/下划线" required />
          </label>
          <label>
            <span className="field-label"><KeyIcon /> 初始密码</span>
            <span className="pwd-wrap">
              <input
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 6 位"
                required
              />
              <button type="button" className="pwd-toggle" onClick={() => setShowPwd((v) => !v)} title={showPwd ? '隐藏密码' : '显示密码'}>
                {showPwd ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </span>
          </label>
          <label>
            <span className="field-label"><ShieldCheckIcon /> 确认密码</span>
            <input type={showPwd ? 'text' : 'password'} value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
          </label>
          <label className="role-check">
            <input type="checkbox" checked={isAdmin} onChange={(e) => setIsAdmin(e.target.checked)} />
            admin 角色（可管理账号）
          </label>
          <button className="primary-btn" disabled={busy || !username.trim() || !password}>
            <UserIcon size={14} /> {busy ? '创建中...' : '创建账号'}
          </button>
        </form>
        {error && <div className="error">{error}</div>}
        {message && <div className="ok">{message}</div>}
      </div>
      <div className="panel-head">
        <h2><UsersIcon size={16} /> 现有账号</h2>
        <span className="panel-desc">共 {users.length} 个账号</span>
      </div>
      {users.length === 0 ? (
        <div className="empty-state"><UsersIcon size={32} /><p>暂无账号</p></div>
      ) : (
        <div className="ag-theme-quartz grid">
          <AgGridReact
            rowData={users}
            columnDefs={[
              { field: 'username', headerName: '用户名', width: 180 },
              { field: 'roles', headerName: '角色', width: 180 },
              { field: 'createdBy', headerName: '创建人' },
              { field: 'createdAt', headerName: '创建时间', width: 200 },
              { headerName: '操作', cellRenderer: ResetRenderer, autoHeight: true },
            ]}
          />
        </div>
      )}
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
        <div>
          <h2><AuditIcon size={16} /> 审计日志</h2>
          <p className="panel-desc">记录每一次查询与导出，SHA-256 哈希链防篡改</p>
        </div>
        <div className="audit-actions">
          <button className="ghost-inline" onClick={load}><RefreshIcon size={13} /> 刷新</button>
          <button className="ghost-inline" onClick={verifyChain}><ShieldCheckIcon size={13} /> 校验链完整性</button>
          {verify && (
            <span className={verify.intact ? 'ok' : 'error'}>
              {verify.intact ? <ShieldCheckIcon size={14} /> : <AlertIcon size={14} />} {verify.message}
            </span>
          )}
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      {records.length === 0 ? (
        <div className="empty-state"><AuditIcon size={32} /><p>暂无审计记录</p><span className="empty-desc">执行查询或导出后，记录会出现在这里</span></div>
      ) : (
        <div className="ag-theme-quartz grid">
          <AgGridReact rowData={records} columnDefs={columnDefs} />
        </div>
      )}
    </div>
  )
}

export default App