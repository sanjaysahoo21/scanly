import { useState, useEffect, useCallback, useMemo } from 'react'
import { Upload, RefreshCw, FolderOpen, ArrowUpDown, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../components/common/Button.jsx'
import DocumentTable from '../components/documents/DocumentTable.jsx'
import FolderManager from '../components/documents/FolderManager.jsx'
import { getDocuments } from '../services/documentService.js'
import '../styles/folders.css'

// ── Sort helpers ──────────────────────────────────────────────────────────────
const SORT_OPTIONS = [
  { value: 'recent',     label: 'Recent first' },
  { value: 'oldest',     label: 'Oldest first' },
  { value: 'name_asc',   label: 'Name A → Z' },
  { value: 'name_desc',  label: 'Name Z → A' },
  { value: 'size_desc',  label: 'Largest first' },
  { value: 'size_asc',   label: 'Smallest first' },
  { value: 'status',     label: 'By status' },
  { value: 'confidence', label: 'By confidence' },
]

function sortDocs(docs, sortKey) {
  const arr = [...docs]
  switch (sortKey) {
    case 'oldest':
      return arr.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
    case 'name_asc':
      return arr.sort((a, b) => (a.fileName || '').localeCompare(b.fileName || ''))
    case 'name_desc':
      return arr.sort((a, b) => (b.fileName || '').localeCompare(a.fileName || ''))
    case 'size_desc':
      return arr.sort((a, b) => (b.fileSizeBytes || 0) - (a.fileSizeBytes || 0))
    case 'size_asc':
      return arr.sort((a, b) => (a.fileSizeBytes || 0) - (b.fileSizeBytes || 0))
    case 'status':
      return arr.sort((a, b) => (a.status || '').localeCompare(b.status || ''))
    case 'confidence':
      return arr.sort((a, b) => (b.confidenceScore ?? -1) - (a.confidenceScore ?? -1))
    case 'recent':
    default:
      return arr.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  }
}

// ── File-type options ─────────────────────────────────────────────────────────
const FILE_TYPES = ['PDF', 'IMAGE']

function DocumentsPage() {
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [sortKey, setSortKey] = useState('recent')

  // Currently selected folder (null = all documents)
  const [selectedFolder, setSelectedFolder] = useState(null)

  const fetchDocuments = useCallback(async (folderId) => {
    setLoading(true)
    setError('')
    try {
      const data = await getDocuments(folderId ?? null)
      setDocuments(data)
    } catch (err) {
      setError(err.message || 'Failed to load documents')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDocuments(selectedFolder?.id ?? null)
  }, [fetchDocuments, selectedFolder])

  // Auto-poll every 5s when any document is still processing
  useEffect(() => {
    const hasPending = documents.some(
      (d) => d.status === 'PENDING' || d.status === 'PROCESSING'
    )
    if (!hasPending) return
    const interval = setInterval(() => fetchDocuments(selectedFolder?.id ?? null), 5000)
    return () => clearInterval(interval)
  }, [documents, fetchDocuments, selectedFolder])

  // Apply status + type filter, then sort — all client-side
  const processed = useMemo(() => {
    let result = documents
    if (statusFilter) result = result.filter(d => d.status === statusFilter)
    if (typeFilter)   result = result.filter(d => d.fileType === typeFilter)
    return sortDocs(result, sortKey)
  }, [documents, statusFilter, typeFilter, sortKey])

  const hasActiveFilters = statusFilter || typeFilter || sortKey !== 'recent'

  const resetFilters = () => {
    setStatusFilter('')
    setTypeFilter('')
    setSortKey('recent')
  }

  const handleFolderSelect = (folder) => {
    setSelectedFolder(folder)
    setStatusFilter('')
    setTypeFilter('')
    setSortKey('recent')
  }

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1>
              {selectedFolder ? (
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <FolderOpen size={24} color={selectedFolder.color} />
                  {selectedFolder.name}
                </span>
              ) : 'Documents'}
            </h1>
            <p>
              {selectedFolder
                ? selectedFolder.description || 'Viewing documents in this folder.'
                : 'View and manage all uploaded documents.'}
            </p>
          </div>
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <Button
              variant="ghost"
              icon={RefreshCw}
              onClick={() => fetchDocuments(selectedFolder?.id ?? null)}
              id="refresh-btn"
            >
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

      {/* ── Layout: Sidebar + Main ──────────────────────────────── */}
      <div className="documents-main animate-fade-in-up stagger-1">
        <section className="documents-folder-bar" aria-label="Document folders">
          <FolderManager
            compact
            variant="toolbar"
            selectedFolderId={selectedFolder?.id ?? null}
            onSelectFolder={handleFolderSelect}
          />
        </section>

        {/* ── Filter + Sort Bar ────────────────────────────────── */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="doc-filter-bar">
            {/* Sort */}
            <div className="doc-filter-group">
              <ArrowUpDown size={14} className="doc-filter-icon" />
              <select
                className="doc-filter-select"
                value={sortKey}
                onChange={e => setSortKey(e.target.value)}
                id="doc-sort-select"
              >
                {SORT_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            {/* Status filter */}
            <select
              className="doc-filter-select"
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              id="doc-status-filter"
            >
              <option value="">All Statuses</option>
              <option value="PENDING">Pending</option>
              <option value="PROCESSING">Processing</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
              <option value="NEEDS_REVIEW">Needs Review</option>
            </select>

            {/* File type filter */}
            <select
              className="doc-filter-select"
              value={typeFilter}
              onChange={e => setTypeFilter(e.target.value)}
              id="doc-type-filter"
            >
              <option value="">All Types</option>
              {FILE_TYPES.map(t => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>

            {/* Count + reset */}
            <div className="doc-filter-meta">
              {!loading && (
                <span className="doc-filter-count">
                  {processed.length} {processed.length === 1 ? 'doc' : 'docs'}
                  {selectedFolder && ` in "${selectedFolder.name}"`}
                </span>
              )}
              {hasActiveFilters && (
                <button className="doc-filter-reset" onClick={resetFilters} id="doc-filter-reset">
                  <X size={12} /> Reset
                </button>
              )}
            </div>
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
              <DocumentTable
                documents={processed}
                selectedFolder={selectedFolder}
                onRefresh={() => fetchDocuments(selectedFolder?.id ?? null)}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default DocumentsPage
