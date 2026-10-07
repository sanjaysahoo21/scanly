import { useState, useEffect, useCallback } from 'react'
import {
  Database, Plus, Trash2, RefreshCw, PlayCircle, CheckCircle2,
  XCircle, ChevronDown, ChevronUp, Loader2, Wifi, WifiOff,
  Clock, AlertTriangle, Settings2, X
} from 'lucide-react'
import {
  getIntegrations, createIntegration, deleteIntegration,
  testConnection, testSavedConnection, pushInvoices, getPushLogs
} from '../services/integrationService.js'
import Button from '../components/common/Button.jsx'
import '../styles/integrations.css'


// ── DB type metadata ──────────────────────────────────────────────────────────
const DB_TYPES = [
  { value: 'postgresql', label: 'PostgreSQL', defaultPort: 5432, color: '#336791' },
  { value: 'mysql',      label: 'MySQL',      defaultPort: 3306, color: '#4479A1' },
]

const EMPTY_FORM = {
  label: '', dbType: 'postgresql', host: '', port: 5432,
  databaseName: '', username: '', password: '',
  useSsl: false, lineItemsStrategy: 'skip', pushMode: 'best_effort',
  targetTable: '',
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

function statusChip(status) {
  const map = {
    SUCCESS: { color: 'var(--color-success, #22c55e)', bg: 'rgba(34,197,94,0.1)', label: 'Success' },
    PARTIAL: { color: '#f59e0b', bg: 'rgba(245,158,11,0.1)', label: 'Partial' },
    FAILED:  { color: 'var(--color-error, #ef4444)',  bg: 'rgba(239,68,68,0.1)',  label: 'Failed' },
  }
  const s = map[status] || { color: 'var(--color-text-muted)', bg: 'transparent', label: status }
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '2px 8px', borderRadius: 20, fontSize: 12, fontWeight: 600,
      color: s.color, background: s.bg, border: `1px solid ${s.color}40`
    }}>
      {status === 'SUCCESS' ? <CheckCircle2 size={11} /> :
       status === 'FAILED'  ? <XCircle size={11} /> : <AlertTriangle size={11} />}
      {s.label}
    </span>
  )
}

// ── Add Integration Wizard ────────────────────────────────────────────────────
function AddIntegrationWizard({ onSaved, onClose }) {
  const [form, setForm]           = useState({ ...EMPTY_FORM })
  const [testState, setTestState] = useState(null)  // null | 'testing' | 'ok' | 'fail'
  const [testMsg, setTestMsg]     = useState('')
  const [saving, setSaving]       = useState(false)
  const [error, setError]         = useState('')

  const dbMeta = DB_TYPES.find(d => d.value === form.dbType) || DB_TYPES[0]

  function set(key, val) {
    setForm(prev => {
      const next = { ...prev, [key]: val }
      // Auto-update port when db type changes
      if (key === 'dbType') {
        next.port = DB_TYPES.find(d => d.value === val)?.defaultPort ?? prev.port
      }
      return next
    })
    setTestState(null)
  }

  async function handleTest() {
    if (!form.host || !form.databaseName || !form.username || !form.password) {
      setError('Fill in host, database, username, and password before testing.')
      return
    }
    setError('')
    setTestState('testing')
    setTestMsg('')
    try {
      const res = await testConnection(form)
      setTestState(res.success ? 'ok' : 'fail')
      setTestMsg(res.success
        ? `Connected in ${res.latencyMs}ms`
        : res.message)
    } catch {
      setTestState('fail')
      setTestMsg('Request failed — check network or backend.')
    }
  }

  async function handleSave() {
    if (!form.label.trim()) { setError('Label is required.'); return }
    if (!form.host.trim())  { setError('Host is required.'); return }
    if (!form.password)     { setError('Password is required.'); return }
    setSaving(true)
    setError('')
    try {
      const saved = await createIntegration({
        ...form,
        port: Number(form.port),
        targetTable: form.targetTable.trim() || null,
      })
      onSaved(saved)
    } catch (e) {
      setError(e.message || 'Failed to save integration.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="integration-wizard-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="integration-wizard">
        {/* Header */}
        <div className="integration-wizard-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Database size={20} color="var(--color-accent)" />
            <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 700 }}>
              Add Database Integration
            </h2>
          </div>
          <button className="wizard-close-btn" onClick={onClose}><X size={18} /></button>
        </div>

        <div className="integration-wizard-body">
          {/* Label */}
          <label className="wizard-label">Label <span style={{ color: 'var(--color-error)' }}>*</span></label>
          <input
            className="input-field"
            placeholder='e.g. "Company ERP" or "Analytics DB"'
            value={form.label}
            onChange={e => set('label', e.target.value)}
          />

          {/* DB Type */}
          <label className="wizard-label">Database Type</label>
          <div className="wizard-dbtype-grid">
            {DB_TYPES.map(db => (
              <button
                key={db.value}
                className={`wizard-dbtype-btn ${form.dbType === db.value ? 'selected' : ''}`}
                onClick={() => set('dbType', db.value)}
                style={form.dbType === db.value ? { borderColor: db.color } : {}}
              >
                <span style={{ fontSize: 18 }}>
                  {db.value === 'postgresql' ? '🐘' : '🐬'}
                </span>
                {db.label}
              </button>
            ))}
          </div>

          {/* Connection fields */}
          <div className="wizard-row">
            <div style={{ flex: 3 }}>
              <label className="wizard-label">Host <span style={{ color: 'var(--color-error)' }}>*</span></label>
              <input
                className="input-field"
                placeholder="localhost or host.docker.internal"
                value={form.host}
                onChange={e => set('host', e.target.value)}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label className="wizard-label">Port</label>
              <input
                className="input-field"
                type="number"
                value={form.port}
                onChange={e => set('port', e.target.value)}
              />
            </div>
          </div>

          <label className="wizard-label">Database Name <span style={{ color: 'var(--color-error)' }}>*</span></label>
          <input
            className="input-field"
            placeholder="e.g. my_erp"
            value={form.databaseName}
            onChange={e => set('databaseName', e.target.value)}
          />

          <div className="wizard-row">
            <div style={{ flex: 1 }}>
              <label className="wizard-label">Username <span style={{ color: 'var(--color-error)' }}>*</span></label>
              <input
                className="input-field"
                value={form.username}
                onChange={e => set('username', e.target.value)}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label className="wizard-label">Password <span style={{ color: 'var(--color-error)' }}>*</span></label>
              <input
                className="input-field"
                type="password"
                value={form.password}
                onChange={e => set('password', e.target.value)}
              />
            </div>
          </div>

          {/* Options */}
          <div className="wizard-row" style={{ gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <label className="wizard-checkbox-label">
              <input type="checkbox" checked={form.useSsl} onChange={e => set('useSsl', e.target.checked)} />
              Use SSL/TLS
            </label>

            <div style={{ flex: 1 }}>
              <label className="wizard-label" style={{ marginBottom: 4 }}>Line Items</label>
              <select className="doc-filter-select" value={form.lineItemsStrategy}
                onChange={e => set('lineItemsStrategy', e.target.value)}>
                <option value="skip">Skip line items</option>
                <option value="json">Store as JSON column</option>
              </select>
            </div>

            <div style={{ flex: 1 }}>
              <label className="wizard-label" style={{ marginBottom: 4 }}>Push Mode</label>
              <select className="doc-filter-select" value={form.pushMode}
                onChange={e => set('pushMode', e.target.value)}>
                <option value="best_effort">Best effort</option>
                <option value="all_or_nothing">All or nothing</option>
              </select>
            </div>
          </div>

          {/* Optional: target table */}
          <label className="wizard-label" style={{ marginTop: 4 }}>
            Target Table <span style={{ fontSize: 12, color: 'var(--color-text-muted)', fontWeight: 400 }}>
              (leave blank to auto-create <code>scanly_invoices</code>)
            </span>
          </label>
          <input
            className="input-field"
            placeholder="Optional — your existing table name"
            value={form.targetTable}
            onChange={e => set('targetTable', e.target.value)}
          />

          {/* Test result */}
          {testState && testState !== 'testing' && (
            <div className={`wizard-test-result ${testState === 'ok' ? 'ok' : 'fail'}`}>
              {testState === 'ok'
                ? <><CheckCircle2 size={14} /> {testMsg}</>
                : <><XCircle size={14} /> {testMsg}</>}
            </div>
          )}

          {/* Error */}
          {error && (
            <div className="wizard-test-result fail">
              <AlertTriangle size={14} /> {error}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="integration-wizard-footer">
          <button
            className="wizard-test-btn"
            onClick={handleTest}
            disabled={testState === 'testing'}
          >
            {testState === 'testing'
              ? <><Loader2 size={14} className="spin" /> Testing…</>
              : <><Wifi size={14} /> Test Connection</>}
          </button>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="wizard-cancel-btn" onClick={onClose}>Cancel</button>
            <button
              className="wizard-save-btn"
              onClick={handleSave}
              disabled={saving}
            >
              {saving
                ? <><Loader2 size={14} className="spin" /> Saving…</>
                : <><Plus size={14} /> Save Integration</>}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Push Result Panel ─────────────────────────────────────────────────────────
function PushResultPanel({ result, onClose }) {
  if (!result) return null
  return (
    <div className={`push-result-panel ${result.status?.toLowerCase()}`}>
      <div className="push-result-header">
        {result.status === 'SUCCESS'
          ? <CheckCircle2 size={16} color="var(--color-success, #22c55e)" />
          : <AlertTriangle size={16} color="#f59e0b" />}
        <strong>Push {result.status === 'SUCCESS' ? 'complete' : 'finished with errors'}</strong>
        <button onClick={onClose} style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer' }}>
          <X size={14} color="var(--color-text-muted)" />
        </button>
      </div>
      <div className="push-result-stats">
        <span className="stat ok">✅ {result.totalSucceeded} succeeded</span>
        {result.totalFailed > 0 && <span className="stat fail">❌ {result.totalFailed} failed</span>}
      </div>
      {result.failures?.length > 0 && (
        <ul className="push-failure-list">
          {result.failures.slice(0, 5).map((f, i) => (
            <li key={i}><code>{f.invoiceNumber || f.invoiceId}</code> — {f.error}</li>
          ))}
          {result.failures.length > 5 && <li>…and {result.failures.length - 5} more</li>}
        </ul>
      )}
    </div>
  )
}

// ── Integration Card ──────────────────────────────────────────────────────────
function IntegrationCard({ cfg, onDeleted, onRefresh }) {
  const [testing, setTesting]     = useState(false)
  const [testResult, setTestResult] = useState(null)
  const [pushing, setPushing]     = useState(false)
  const [pushResult, setPushResult] = useState(null)
  const [logs, setLogs]           = useState([])
  const [showLogs, setShowLogs]   = useState(false)
  const [loadingLogs, setLoadingLogs] = useState(false)
  const [deleting, setDeleting]   = useState(false)

  const dbMeta = DB_TYPES.find(d => d.value === cfg.dbType) || DB_TYPES[0]

  async function handleTest() {
    setTesting(true)
    setTestResult(null)
    try {
      const res = await testSavedConnection(cfg.id)
      setTestResult(res)
    } catch {
      setTestResult({ success: false, message: 'Request failed' })
    } finally {
      setTesting(false)
    }
  }

  async function handlePush() {
    setPushing(true)
    setPushResult(null)
    try {
      const res = await pushInvoices(cfg.id, { pushAll: true })
      setPushResult(res)
      onRefresh()
    } catch (e) {
      setPushResult({ status: 'FAILED', totalAttempted: 0, totalSucceeded: 0,
        totalFailed: 0, failures: [{ error: e.message }] })
    } finally {
      setPushing(false)
    }
  }

  async function handleShowLogs() {
    if (!showLogs) {
      setLoadingLogs(true)
      try {
        const data = await getPushLogs(cfg.id)
        setLogs(data)
      } catch { setLogs([]) }
      finally { setLoadingLogs(false) }
    }
    setShowLogs(v => !v)
  }

  async function handleDelete() {
    if (!window.confirm(`Delete integration "${cfg.label}"? This cannot be undone.`)) return
    setDeleting(true)
    try {
      await deleteIntegration(cfg.id)
      onDeleted(cfg.id)
    } catch { setDeleting(false) }
  }

  const emoji = cfg.dbType === 'postgresql' ? '🐘' : '🐬'
  const tierLabel = cfg.tier === 'AUTO_CREATE' ? 'Auto-create table' : `→ ${cfg.targetTable}`

  return (
    <div className="integration-card animate-fade-in-up">
      {/* Card header */}
      <div className="integration-card-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 24 }}>{emoji}</span>
          <div>
            <div style={{ fontWeight: 700, fontSize: 'var(--text-base)', color: 'var(--color-text)' }}>
              {cfg.label}
            </div>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              {cfg.host}:{cfg.port}/{cfg.databaseName}
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          {/* Test */}
          <button className="integration-icon-btn" onClick={handleTest}
            disabled={testing} title="Test connection">
            {testing
              ? <Loader2 size={14} className="spin" />
              : <Wifi size={14} />}
          </button>
          {/* Push */}
          <button className="integration-icon-btn primary" onClick={handlePush}
            disabled={pushing} title="Push all invoices now">
            {pushing
              ? <Loader2 size={14} className="spin" />
              : <PlayCircle size={14} />}
          </button>
          {/* Delete */}
          <button className="integration-icon-btn danger" onClick={handleDelete}
            disabled={deleting} title="Delete integration">
            {deleting ? <Loader2 size={14} className="spin" /> : <Trash2 size={14} />}
          </button>
        </div>
      </div>

      {/* Meta row */}
      <div className="integration-card-meta">
        <span className="integration-badge">{dbMeta.label}</span>
        <span className="integration-badge muted">{tierLabel}</span>
        {cfg.useSsl && <span className="integration-badge ssl">SSL</span>}
        <span style={{ marginLeft: 'auto', fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
          <Clock size={11} style={{ verticalAlign: 'middle', marginRight: 3 }} />
          Added {fmt(cfg.createdAt)}
        </span>
      </div>

      {/* Connection test result */}
      {testResult && (
        <div className={`wizard-test-result ${testResult.success ? 'ok' : 'fail'}`}
          style={{ margin: '8px 0 0 0' }}>
          {testResult.success
            ? <><CheckCircle2 size={13} /> Connected in {testResult.latencyMs}ms</>
            : <><WifiOff size={13} /> {testResult.message}</>}
        </div>
      )}

      {/* Push result */}
      {pushResult && (
        <PushResultPanel result={pushResult} onClose={() => setPushResult(null)} />
      )}

      {/* Push logs toggle */}
      <button className="integration-logs-toggle" onClick={handleShowLogs}>
        {loadingLogs
          ? <><Loader2 size={12} className="spin" /> Loading…</>
          : showLogs
          ? <><ChevronUp size={12} /> Hide push history</>
          : <><ChevronDown size={12} /> Show push history</>}
      </button>

      {showLogs && (
        <div className="integration-logs">
          {logs.length === 0
            ? <div style={{ color: 'var(--color-text-muted)', fontSize: 'var(--text-xs)', padding: '8px 0' }}>
                No pushes recorded yet.
              </div>
            : logs.map(l => (
              <div key={l.id} className="integration-log-row">
                {statusChip(l.status)}
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', flex: 1 }}>
                  {l.totalSucceeded}/{l.totalAttempted} invoices
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  {fmt(l.pushedAt)}
                </span>
              </div>
            ))}
        </div>
      )}
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────
function IntegrationsPage() {
  const [integrations, setIntegrations] = useState([])
  const [loading, setLoading]           = useState(true)
  const [showWizard, setShowWizard]     = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getIntegrations()
      setIntegrations(data)
    } catch { /* ignore */ }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  function handleSaved(newCfg) {
    setIntegrations(prev => [newCfg, ...prev])
    setShowWizard(false)
  }

  function handleDeleted(id) {
    setIntegrations(prev => prev.filter(c => c.id !== id))
  }

  return (
    <div className="page-content">
      {/* Header */}
      <div className="page-header animate-fade-in-up">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Database size={24} color="var(--color-accent)" /> Integrations
            </h1>
            <p>Push extracted invoice data directly into your own database — local or cloud-hosted.</p>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="ghost" icon={RefreshCw} onClick={load} disabled={loading}>
              Refresh
            </Button>
            <Button variant="primary" icon={Plus} onClick={() => setShowWizard(true)}>
              Add Integration
            </Button>
          </div>
        </div>
      </div>

      {/* Loading */}
      {loading && (
        <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--color-text-muted)' }}>
          <Loader2 size={28} className="spin" />
        </div>
      )}

      {/* Empty state */}
      {!loading && integrations.length === 0 && (
        <div className="integration-empty animate-fade-in-up">
          <Database size={48} color="var(--color-text-muted)" style={{ opacity: 0.4 }} />
          <h3>No integrations yet</h3>
          <p>Connect a PostgreSQL or MySQL database to push your extracted invoice data directly.</p>
          <Button variant="primary" icon={Plus} onClick={() => setShowWizard(true)}>
            Add your first integration
          </Button>
        </div>
      )}

      {/* Cards */}
      {!loading && integrations.length > 0 && (
        <div className="integration-list animate-fade-in-up stagger-1">
          {integrations.map(cfg => (
            <IntegrationCard
              key={cfg.id}
              cfg={cfg}
              onDeleted={handleDeleted}
              onRefresh={load}
            />
          ))}
        </div>
      )}

      {/* Wizard modal */}
      {showWizard && (
        <AddIntegrationWizard
          onSaved={handleSaved}
          onClose={() => setShowWizard(false)}
        />
      )}
    </div>
  )
}

export default IntegrationsPage
