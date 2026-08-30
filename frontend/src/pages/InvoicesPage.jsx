import { Receipt, Eye } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getInvoices } from '../services/invoiceService.js'
import StatusBadge from '../components/common/StatusBadge.jsx'

function InvoicesPage() {
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getInvoices().then((data) => setInvoices(data.invoices || []))
      .catch((err) => setError(err.message)).finally(() => setLoading(false))
  }, [])

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Invoices</h1><p>Browse extracted invoice data and manage audits.</p>
      </div>
      {error && <div className="upload-status upload-status-error">{error}</div>}
      {loading ? <p>Loading invoices...</p> : invoices.length === 0 ? (
        <div className="table-container"><div className="empty-state"><Receipt className="empty-state-icon" /><h3>No invoices yet</h3><p>Invoices appear after document processing completes.</p></div></div>
      ) : (
        <div className="table-container"><table><thead><tr><th>Invoice</th><th>Vendor</th><th>Total</th><th>Status</th><th></th></tr></thead>
          <tbody>{invoices.map((invoice) => <tr key={invoice.id}><td>{invoice.invoiceNumber || '—'}</td><td>{invoice.vendorName || '—'}</td><td>{invoice.totalAmount ?? '—'} {invoice.currency || ''}</td><td><StatusBadge status={invoice.isAudited ? 'COMPLETED' : 'NEEDS_REVIEW'} /></td><td><Link to={`/invoices/${invoice.id}`} aria-label="View invoice"><Eye size={16} /></Link></td></tr>)}</tbody>
        </table></div>
      )}
    </div>
  )
}

export default InvoicesPage
