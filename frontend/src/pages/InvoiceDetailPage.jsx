import { useParams } from 'react-router-dom'
import { useState } from 'react'
import InvoiceForm from '../components/invoices/InvoiceForm.jsx'
import LineItemsTable from '../components/invoices/LineItemsTable.jsx'
import StatusBadge from '../components/common/StatusBadge.jsx'
import Button from '../components/common/Button.jsx'
import { ArrowLeft, CheckCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import '../styles/invoices.css'

function InvoiceDetailPage() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState({})

  // TODO: Fetch invoice data from API using `id`

  const handleSave = () => {
    // TODO: Wire to invoice update API
    console.log('Saving invoice:', invoice)
  }

  const handleApprove = () => {
    // TODO: Wire to invoice approve API
    console.log('Approving invoice:', id)
  }

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <Link to="/invoices">
          <Button variant="ghost" size="sm" icon={ArrowLeft}>
            Back to Invoices
          </Button>
        </Link>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginTop: 'var(--space-4)' }}>
          <div>
            <h1>Invoice {id}</h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
              <StatusBadge status="PENDING" />
            </div>
          </div>
          <Button variant="primary" icon={CheckCircle} onClick={handleApprove} id="approve-invoice-btn">
            Approve
          </Button>
        </div>
      </div>

      <div className="invoice-detail-container animate-fade-in-up stagger-1">
        <div className="card" style={{ padding: 'var(--space-6)' }}>
          <InvoiceForm
            invoice={invoice}
            onChange={setInvoice}
            onSave={handleSave}
          />
          <LineItemsTable
            items={invoice.line_items || []}
            onChange={(items) => setInvoice({ ...invoice, line_items: items })}
          />
        </div>
      </div>
    </div>
  )
}

export default InvoiceDetailPage
