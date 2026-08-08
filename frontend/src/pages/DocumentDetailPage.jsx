import { useParams } from 'react-router-dom'
import { useState } from 'react'
import AuditViewer from '../components/audit/AuditViewer.jsx'
import AuditLogTable from '../components/audit/AuditLogTable.jsx'

function DocumentDetailPage() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState({})

  // TODO: Fetch document and invoice data from API using `id`

  const handleSave = () => {
    // TODO: Wire to invoice update API
    console.log('Saving invoice:', invoice)
  }

  const handleApprove = () => {
    // TODO: Wire to invoice approve API
    console.log('Approving invoice for document:', id)
  }

  const handleReprocess = () => {
    // TODO: Wire to document reprocess API
    console.log('Reprocessing document:', id)
  }

  return (
    <div className="page-content">
      <AuditViewer
        document={{ file_name: `Document ${id}`, status: 'PENDING' }}
        invoice={invoice}
        onInvoiceChange={setInvoice}
        onSave={handleSave}
        onApprove={handleApprove}
        onReprocess={handleReprocess}
      />

      {/* Audit History */}
      <div className="audit-history-section animate-fade-in-up stagger-3">
        <h3>Audit History</h3>
        <AuditLogTable logs={[]} />
      </div>
    </div>
  )
}

export default DocumentDetailPage
