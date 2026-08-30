import { useParams, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import AuditViewer from '../components/audit/AuditViewer.jsx'
import AuditLogTable from '../components/audit/AuditLogTable.jsx'
import { getToken } from '../services/authService.js'

async function fetchDocumentDetail(id) {
  const token = getToken()
  const res = await fetch(`/api/v1/documents/${id}/detail`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const text = await res.text()
  if (!res.ok) throw new Error('Failed to load document')
  return text ? JSON.parse(text) : {}
}

async function saveInvoice(docId, invoice) {
  const token = getToken()
  const res = await fetch(`/api/v1/documents/${docId}/invoice`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(invoice),
  })
  const text = await res.text()
  if (!res.ok) throw new Error('Failed to save invoice')
  return text ? JSON.parse(text) : {}
}

function DocumentDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [document, setDocument] = useState(null)
  const [invoice, setInvoice] = useState({})
  const [auditLogs, setAuditLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveMsg, setSaveMsg] = useState('')

  useEffect(() => {
    fetchDocumentDetail(id)
      .then(({ document, invoice, auditLogs }) => {
        setDocument(document)
        setInvoice(invoice || {})
        setAuditLogs(auditLogs || [])
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [id])

  const handleSave = async () => {
    setSaving(true)
    setSaveMsg('')
    try {
      const saved = await saveInvoice(id, invoice)
      setInvoice(saved)
      setSaveMsg('Saved successfully!')
      setTimeout(() => setSaveMsg(''), 3000)
    } catch (err) {
      setSaveMsg('Save failed: ' + err.message)
    } finally {
      setSaving(false)
    }
  }

  const handleApprove = () => {
    // TODO: Feature 9 — approve endpoint
    alert('Approve feature coming soon!')
  }

  const handleReprocess = async () => {
    setSaveMsg('Reprocessing started...')
    try {
      const token = getToken()
      const res = await fetch(`/api/v1/documents/${id}/reprocess`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` }
      })
      if (res.ok) {
        setSaveMsg('Reprocessing queued. Refreshing in 3 seconds...')
        setTimeout(() => {
          fetchDocumentDetail(id).then(({ document, invoice, auditLogs }) => {
            setDocument(document)
            setInvoice(invoice || {})
            setAuditLogs(auditLogs || [])
            setSaveMsg('')
          })
        }, 3000)
      } else {
        setSaveMsg('Failed to trigger reprocessing')
      }
    } catch (err) {
      setSaveMsg('Error: ' + err.message)
    }
  }

  if (loading) {
    return (
      <div className="page-content">
        <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--color-text-muted)' }}>
          Loading document...
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="page-content">
        <div className="upload-status upload-status-error">{error}</div>
      </div>
    )
  }

  return (
    <div className="page-content">
      {saveMsg && (
        <div className={`upload-status ${saveMsg.startsWith('Save failed') ? 'upload-status-error' : 'upload-status-success'} animate-fade-in-up`}>
          {saveMsg}
        </div>
      )}

      <AuditViewer
        document={document}
        invoice={invoice}
        onInvoiceChange={setInvoice}
        onSave={handleSave}
        onApprove={handleApprove}
        onReprocess={handleReprocess}
        saving={saving}
      />

      {/* Audit History */}
      <div className="audit-history-section animate-fade-in-up stagger-3">
        <h3>Audit History</h3>
        <AuditLogTable logs={auditLogs} />
      </div>
    </div>
  )
}

export default DocumentDetailPage
