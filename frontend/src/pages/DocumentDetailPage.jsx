import { useParams } from 'react-router-dom'
import { useState, useEffect } from 'react'
import AuditViewer from '../components/audit/AuditViewer.jsx'
import AuditLogTable from '../components/audit/AuditLogTable.jsx'
import { getToken } from '../services/authService.js'

async function apiRequest(path, options = {}) {
  const res = await fetch(path, { ...options, headers: { Authorization: `Bearer ${getToken()}`, ...(options.headers || {}) } })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || 'Request failed')
  return data
}

function DocumentDetailPage() {
  const { id } = useParams()
  const [document, setDocument] = useState(null)
  const [invoice, setInvoice] = useState({})
  const [auditLogs, setAuditLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveMsg, setSaveMsg] = useState('')

  const load = async () => {
    const data = await apiRequest(`/api/v1/documents/${id}/detail`)
    setDocument(data.document)
    setInvoice(data.invoice || {})
    setAuditLogs(data.auditLogs || [])
  }

  useEffect(() => { load().catch((err) => setError(err.message)).finally(() => setLoading(false)) }, [id])

  const runMutation = async (path, method, body, successMessage) => {
    setSaving(true); setSaveMsg('')
    try {
      const data = await apiRequest(path, { method, ...(body ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}) })
      if (data.invoiceNumber !== undefined) setInvoice(data)
      setSaveMsg(successMessage)
      await load()
    } catch (err) {
      setSaveMsg(`Save failed: ${err.message}`)
    } finally { setSaving(false) }
  }

  if (loading) return <div className="page-content">Loading document...</div>
  if (error) return <div className="page-content"><div className="upload-status upload-status-error">{error}</div></div>

  return <div className="page-content">
    {saveMsg && <div className={`upload-status ${saveMsg.startsWith('Save failed') ? 'upload-status-error' : 'upload-status-success'}`}>{saveMsg}</div>}
    <AuditViewer document={document} invoice={invoice} onInvoiceChange={setInvoice}
      onSave={() => runMutation(`/api/v1/documents/${id}/invoice`, 'PUT', invoice, 'Saved successfully!')}
      onApprove={() => runMutation(`/api/v1/documents/${id}/approve`, 'POST', null, 'Invoice approved successfully!')}
      onReprocess={() => runMutation(`/api/v1/documents/${id}/reprocess`, 'POST', null, 'Reprocessing started.')}
      saving={saving} />
    <div className="audit-history-section"><h3>Audit History</h3><AuditLogTable logs={auditLogs} /></div>
  </div>
}

export default DocumentDetailPage
