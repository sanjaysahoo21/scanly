import { FileText, CheckCircle, RefreshCw } from 'lucide-react'
import StatusBadge from '../common/StatusBadge.jsx'
import Button from '../common/Button.jsx'
import InvoiceForm from '../invoices/InvoiceForm.jsx'
import LineItemsTable from '../invoices/LineItemsTable.jsx'
import '../../styles/audit.css'

function AuditViewer({
  document = {},
  invoice = {},
  onInvoiceChange,
  onSave,
  onApprove,
  onReprocess,
}) {
  return (
    <div className="audit-viewer animate-fade-in">
      {/* Header */}
      <div className="audit-viewer-header">
        <div className="audit-viewer-header-left">
          <h2>{document.file_name || 'Document'}</h2>
          <div className="audit-viewer-meta">
            <StatusBadge status={document.status || 'PENDING'} />
            {document.confidence_score != null && (
              <span className="audit-confidence">
                Confidence: {(document.confidence_score * 100).toFixed(0)}%
              </span>
            )}
          </div>
        </div>
        <div className="audit-viewer-header-right">
          {onReprocess && (
            <Button variant="secondary" icon={RefreshCw} onClick={onReprocess} size="sm">
              Reprocess
            </Button>
          )}
          {onApprove && (
            <Button variant="primary" icon={CheckCircle} onClick={onApprove} size="sm" id="approve-btn">
              Approve
            </Button>
          )}
        </div>
      </div>

      {/* Split Panel */}
      <div className="split-panel">
        {/* Left — PDF Viewer Placeholder */}
        <div className="split-panel-left">
          <div className="pdf-viewer-placeholder">
            <FileText size={48} strokeWidth={1} />
            <h3>Document Preview</h3>
            <p>The original uploaded document will be displayed here.</p>
          </div>
        </div>

        {/* Right — Editable Form */}
        <div className="split-panel-right">
          <InvoiceForm
            invoice={invoice}
            onChange={onInvoiceChange}
            onSave={onSave}
          />
          <LineItemsTable
            items={invoice.line_items || []}
            onChange={(items) =>
              onInvoiceChange && onInvoiceChange({ ...invoice, line_items: items })
            }
          />
        </div>
      </div>
    </div>
  )
}

export default AuditViewer
