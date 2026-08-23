import { useState, useEffect } from 'react'
import { Upload, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../components/common/Button.jsx'
import DocumentTable from '../components/documents/DocumentTable.jsx'
import { getDocuments } from '../services/documentService.js'

function DocumentsPage() {
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const fetchDocuments = async () => {
    setLoading(true)
    setError('')
    try {
      const data = await getDocuments()
      setDocuments(data)
    } catch (err) {
      setError(err.message || 'Failed to load documents')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDocuments()
  }, [])

  // Apply status filter client-side
  const filtered = statusFilter
    ? documents.filter((d) => d.status === statusFilter)
    : documents

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1>Documents</h1>
            <p>View and manage all uploaded documents.</p>
          </div>
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <Button variant="ghost" icon={RefreshCw} onClick={fetchDocuments} id="refresh-btn">
              Refresh
            </Button>
            <Link to="/documents/upload">
              <Button variant="primary" icon={Upload}>
                Upload Documents
              </Button>
            </Link>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="filter-bar animate-fade-in-up stagger-1">
        <select
          className="input-field"
          style={{ width: 'auto', height: '36px', fontSize: 'var(--text-sm)' }}
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="PROCESSING">Processing</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
          <option value="NEEDS_REVIEW">Needs Review</option>
        </select>
        {!loading && (
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>
            {filtered.length} {filtered.length === 1 ? 'document' : 'documents'}
          </span>
        )}
      </div>

      {/* Error */}
      {error && (
        <div className="upload-status upload-status-error animate-fade-in-up">
          {error}
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--color-text-muted)' }}>
          Loading documents...
        </div>
      )}

      {/* Document Table */}
      {!loading && (
        <div className="animate-fade-in-up stagger-2">
          <DocumentTable documents={filtered} />
        </div>
      )}
    </div>
  )
}

export default DocumentsPage
