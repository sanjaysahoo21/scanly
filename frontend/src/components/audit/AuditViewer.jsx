import { useEffect, useState } from 'react'
import { FileText, CheckCircle, RefreshCw, Download, AlertTriangle, Loader, Eye, Edit3 } from 'lucide-react'
import StatusBadge from '../common/StatusBadge.jsx'
import Button from '../common/Button.jsx'
import InvoiceForm from '../invoices/InvoiceForm.jsx'
import LineItemsTable from '../invoices/LineItemsTable.jsx'
import { getToken } from '../../services/authService.js'
import '../../styles/audit.css'

function AuditViewer({
  document = {},
  invoice = {},
  onInvoiceChange,
  onSave,
  onApprove,
  onReprocess,
  saving = false,
}) {
  const [activeTab, setActiveTab] = useState('preview')
  const [pdfBlobUrl, setPdfBlobUrl] = useState(null)
  const [pdfLoading, setPdfLoading] = useState(false)
  const [pdfError, setPdfError] = useState(false)

  const canPreview = document.id && (
    document.status === 'COMPLETED' ||
    document.status === 'NEEDS_REVIEW' ||
    document.status === 'FAILED'
  )

  useEffect(() => {
    if (!canPreview) {
      setActiveTab('form')
      return
    }
    let objectUrl = null
    setPdfLoading(true)
    setPdfError(false)
    setPdfBlobUrl(null)

    fetch(`/api/v1/documents/${document.id}/file`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.blob()
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob)
        setPdfBlobUrl(objectUrl)
      })
      .catch(() => {
        setPdfError(true)
        setActiveTab('form')
      })
      .finally(() => setPdfLoading(false))

    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [document.id, document.status])

  const handleDownload = () => {
    if (!pdfBlobUrl) return
    const a = window.document.createElement('a')
    a.href = pdfBlobUrl
    a.download = document.fileName || 'document.pdf'
    a.click()
  }

  return (
    <div className="audit-viewer animate-fade-in">
      {/* Header */}
      <div className="audit-viewer-header">
        <div className="audit-viewer-header-left">
          <h2>{document.fileName || 'Document'}</h2>
          <div className="audit-viewer-meta">
            <StatusBadge status={document.status || 'PENDING'} />
            {document.confidenceScore != null && (
              <span className="audit-confidence">
                Confidence: {(document.confidenceScore * 100).toFixed(0)}%
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
          {pdfBlobUrl && (
            <Button variant="ghost" icon={Download} onClick={handleDownload} size="sm">
              Download
            </Button>
          )}
          {onApprove && (
            <Button variant="primary" icon={CheckCircle} onClick={onApprove} size="sm" id="approve-btn">
              Approve
            </Button>
          )}
        </div>
      </div>

      {/* Tab Bar */}
      <div className="audit-tabs">
        <button
          className={`audit-tab ${activeTab === 'preview' ? 'audit-tab-active' : ''}`}
          onClick={() => setActiveTab('preview')}
          disabled={!canPreview && !pdfLoading}
        >
          <Eye size={15} />
          Preview
          {pdfLoading && <Loader size={13} style={{ animation: 'spin 1s linear infinite' }} />}
        </button>
        <button
          className={`audit-tab ${activeTab === 'form' ? 'audit-tab-active' : ''}`}
          onClick={() => setActiveTab('form')}
        >
          <Edit3 size={15} />
          Edit Invoice
        </button>
      </div>

      {/* Tab Content */}
      {activeTab === 'preview' && (
        <div className="audit-preview-panel">
          {pdfLoading && (
            <div className="pdf-viewer-placeholder">
              <Loader size={40} strokeWidth={1.5} style={{ animation: 'spin 1s linear infinite' }} />
              <h3>Loading Preview…</h3>
            </div>
          )}
          {!pdfLoading && pdfBlobUrl && (
            <iframe
              src={pdfBlobUrl}
              title={`Preview: ${document.fileName}`}
              className="pdf-iframe"
            />
          )}
          {!pdfLoading && pdfError && (
            <div className="pdf-viewer-placeholder">
              <AlertTriangle size={48} strokeWidth={1} />
              <h3>Preview Unavailable</h3>
              <p>Could not load the document file. Try reprocessing it.</p>
            </div>
          )}
          {!pdfLoading && !pdfBlobUrl && !pdfError && (
            <div className="pdf-viewer-placeholder">
              {document.status === 'PENDING' || document.status === 'PROCESSING' ? (
                <>
                  <Loader size={48} strokeWidth={1} style={{ animation: 'spin 1s linear infinite' }} />
                  <h3>Processing…</h3>
                  <p>AI is extracting data. Preview will appear once complete.</p>
                </>
              ) : (
                <>
                  <FileText size={48} strokeWidth={1} />
                  <h3>No Preview Available</h3>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {activeTab === 'form' && (
        <div className="audit-form-panel">
          <InvoiceForm
            invoice={invoice}
            onChange={onInvoiceChange}
            onSave={onSave}
            saving={saving}
          />
          <LineItemsTable
            items={invoice.lineItems || []}
            onChange={(items) =>
              onInvoiceChange && onInvoiceChange({ ...invoice, lineItems: items })
            }
          />
        </div>
      )}
    </div>
  )
}

export default AuditViewer
