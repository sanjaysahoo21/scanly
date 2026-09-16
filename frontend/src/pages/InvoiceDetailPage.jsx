import { useParams, Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import InvoiceForm from '../components/invoices/InvoiceForm.jsx'
import LineItemsTable from '../components/invoices/LineItemsTable.jsx'
import Button from '../components/common/Button.jsx'
import ExportDropdown from '../components/common/ExportDropdown.jsx'
import { ArrowLeft, CheckCircle, AlertTriangle, CheckCheck, RefreshCw } from 'lucide-react'
import { getInvoice } from '../services/invoiceService.js'
import { getToken } from '../services/authService.js'
import '../styles/invoices.css'

// ── Validation Panel ──────────────────────────────────────────────────────────
function ValidationPanel({ invoice, onRevalidate, loading }) {
  const { hasValidationErrors, validationIssues } = invoice

  // Parse issues JSON string → array
  let issues = []
  if (validationIssues) {
    try { issues = JSON.parse(validationIssues) } catch { issues = [] }
  }

  // Not yet validated
  if (hasValidationErrors === null || hasValidationErrors === undefined) {
    return (
      <div className="validation-panel validation-panel--pending">
        <div className="validation-panel__header">
          <RefreshCw size={15} />
          <span>Math validation not run yet</span>
          <button className="validation-rerun-btn" onClick={onRevalidate} disabled={loading}>
            {loading ? 'Running…' : 'Run now'}
          </button>
        </div>
      </div>
    )
  }

  // All checks passed
  if (!hasValidationErrors) {
    return (
      <div className="validation-panel validation-panel--ok">
        <div className="validation-panel__header">
          <CheckCheck size={15} />
          <span>All math &amp; tax checks passed</span>
          <button className="validation-rerun-btn" onClick={onRevalidate} disabled={loading}>
            {loading ? 'Running…' : 'Re-run'}
          </button>
        </div>
      </div>
    )
  }

  // Issues found
  return (
    <div className="validation-panel validation-panel--error">
      <div className="validation-panel__header">
        <AlertTriangle size={15} />
        <span>{issues.length} validation issue{issues.length !== 1 ? 's' : ''} found</span>
        <button className="validation-rerun-btn" onClick={onRevalidate} disabled={loading}>
          {loading ? 'Running…' : 'Re-run'}
        </button>
      </div>
      <ul className="validation-issue-list">
        {issues.map((issue, i) => (
          <li key={i} className="validation-issue-item">
            <AlertTriangle size={12} className="validation-issue-icon" />
            {issue}
          </li>
        ))}
      </ul>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────
function InvoiceDetailPage() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [validating, setValidating] = useState(false)

  useEffect(() => { getInvoice(id).then(setInvoice).catch((err) => setError(err.message)) }, [id])

  const mutate = async (path, method, body) => {
    const res = await fetch(path, {
      method,
      headers: {
        Authorization: `Bearer ${getToken()}`,
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
    const text = await res.text()
    const data = text ? JSON.parse(text) : {}
    if (!res.ok) throw new Error(data.message || 'Request failed')
    return data
  }

  const handleSave = async () => {
    setSaving(true); setError('')
    try { setInvoice(await mutate(`/api/v1/documents/${invoice.documentId}/invoice`, 'PUT', invoice)) }
    catch (err) { setError(err.message) }
    finally { setSaving(false) }
  }

  const handleApprove = async () => {
    setSaving(true); setError('')
    try { setInvoice(await mutate(`/api/v1/documents/${invoice.documentId}/approve`, 'POST')) }
    catch (err) { setError(err.message) }
    finally { setSaving(false) }
  }

  const handleRevalidate = async () => {
    setValidating(true); setError('')
    try { setInvoice(await mutate(`/api/v1/invoices/${id}/validate`, 'POST')) }
    catch (err) { setError(err.message) }
    finally { setValidating(false) }
  }

  if (error) return <div className="page-content"><div className="upload-status upload-status-error">{error}</div></div>
  if (!invoice) return <div className="page-content">Loading invoice...</div>

  return (
    <div className="page-content">
      <Link to="/invoices"><Button variant="ghost" size="sm" icon={ArrowLeft}>Back to Invoices</Button></Link>

      <div className="page-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
        <h1>Invoice {invoice.invoiceNumber || id}</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <ExportDropdown endpoint={`/api/v1/invoices/${id}/export`} filename={`invoice-${invoice.invoiceNumber || id}`} />
          <Button variant="primary" icon={CheckCircle} onClick={handleApprove} loading={saving} disabled={invoice.isAudited}>Approve</Button>
        </div>
      </div>

      {/* Validation Panel */}
      <ValidationPanel invoice={invoice} onRevalidate={handleRevalidate} loading={validating} />

      <div className="card" style={{ padding: 'var(--space-6)' }}>
        <InvoiceForm invoice={invoice} onChange={setInvoice} onSave={handleSave} saving={saving} />
        <LineItemsTable items={invoice.lineItems || []} onChange={(lineItems) => setInvoice({ ...invoice, lineItems })} />
      </div>
    </div>
  )
}

export default InvoiceDetailPage
