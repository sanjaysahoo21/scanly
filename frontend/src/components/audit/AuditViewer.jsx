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
  const [fileBlobUrl, setFileBlobUrl] = useState(null)
  const [fileLoading, setFileLoading] = useState(false)
  const [fileError, setFileError] = useState(false)

  // Determine if this is an image or a PDF
  const isImage = (
    document.fileType === 'JPG' ||
    document.fileType === 'PNG' ||
    document.fileType === 'JPEG' ||
    /\.(jpg|jpeg|png|gif|webp)$/i.test(document.fileName || '')
  )

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
    setFileLoading(true)
    setFileError(false)
    setFileBlobUrl(null)

    fetch(`/api/v1/documents/${document.id}/file`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.blob()
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob)
        setFileBlobUrl(objectUrl)
      })
      .catch(() => {
        setFileError(true)
        setActiveTab('form')
      })
      .finally(() => setFileLoading(false))

    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [document.id, document.status])

  const handleDownload = () => {
    if (!fileBlobUrl) return
    const a = window.document.createElement('a')
    a.href = fileBlobUrl
    a.download = document.fileName || 'document'
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
          {fileBlobUrl && (
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
          disabled={!canPreview && !fileLoading}
        >
          <Eye size={15} />
          Preview
          {fileLoading && <Loader size={13} style={{ animation: 'spin 1s linear infinite' }} />}
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
          {fileLoading && (
            <div className="pdf-viewer-placeholder">
              <Loader size={40} strokeWidth={1.5} style={{ animation: 'spin 1s linear infinite' }} />
              <h3>Loading Preview…</h3>
            </div>
          )}
          {!fileLoading && fileBlobUrl && isImage && (
            <div className="image-preview-wrapper">
              <img
                src={fileBlobUrl}
                alt={`Preview: ${document.fileName}`}
                className="image-preview"
              />
            </div>
          )}
          {!fileLoading && fileBlobUrl && !isImage && (
            <iframe
              src={fileBlobUrl}
              title={`Preview: ${document.fileName}`}
              className="pdf-iframe"
            />
          )}
          {!fileLoading && fileError && (
            <div className="pdf-viewer-placeholder">
              <AlertTriangle size={48} strokeWidth={1} />
              <h3>Preview Unavailable</h3>
              <p>Could not load the document file. Try reprocessing it.</p>
            </div>
          )}
          {!fileLoading && !fileBlobUrl && !fileError && (
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
