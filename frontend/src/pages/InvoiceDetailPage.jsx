import { useParams, Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import InvoiceForm from '../components/invoices/InvoiceForm.jsx'
import LineItemsTable from '../components/invoices/LineItemsTable.jsx'
import Button from '../components/common/Button.jsx'
import { ArrowLeft, CheckCircle } from 'lucide-react'
import { getInvoice } from '../services/invoiceService.js'
import { getToken } from '../services/authService.js'
import '../styles/invoices.css'

function InvoiceDetailPage() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => { getInvoice(id).then(setInvoice).catch((err) => setError(err.message)) }, [id])

  const mutate = async (path, method, body) => {
    const res = await fetch(path, { method, headers: { Authorization: `Bearer ${getToken()}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) })
    const text = await res.text(); const data = text ? JSON.parse(text) : {}
    if (!res.ok) throw new Error(data.message || 'Request failed')
    return data
  }
  const handleSave = async () => { setSaving(true); setError(''); try { setInvoice(await mutate(`/api/v1/documents/${invoice.documentId}/invoice`, 'PUT', invoice)) } catch (err) { setError(err.message) } finally { setSaving(false) } }
  const handleApprove = async () => { setSaving(true); setError(''); try { setInvoice(await mutate(`/api/v1/documents/${invoice.documentId}/approve`, 'POST')) } catch (err) { setError(err.message) } finally { setSaving(false) } }

  if (error) return <div className="page-content"><div className="upload-status upload-status-error">{error}</div></div>
  if (!invoice) return <div className="page-content">Loading invoice...</div>
  return <div className="page-content"><Link to="/invoices"><Button variant="ghost" size="sm" icon={ArrowLeft}>Back to Invoices</Button></Link>
    <div className="page-header"><h1>Invoice {invoice.invoiceNumber || id}</h1><Button variant="primary" icon={CheckCircle} onClick={handleApprove} loading={saving} disabled={invoice.isAudited}>Approve</Button></div>
    <div className="card" style={{ padding: 'var(--space-6)' }}><InvoiceForm invoice={invoice} onChange={setInvoice} onSave={handleSave} saving={saving} /><LineItemsTable items={invoice.lineItems || []} onChange={(lineItems) => setInvoice({ ...invoice, lineItems })} /></div>
  </div>
}

export default InvoiceDetailPage
