import { useState, useMemo } from 'react'
import { FileText, Eye, Move, FolderMinus, Trash2, Search, CheckSquare, Square, ChevronDown } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import StatusBadge from '../common/StatusBadge.jsx'
import MoveFolderModal from './MoveFolderModal.jsx'
import { deleteDocument } from '../../services/documentService.js'
import { removeDocumentFromFolder } from '../../services/folderService.js'
import '../../styles/documents.css'

/**
 * DocumentTable
 *
 * Props:
 *   documents      — array of document objects
 *   selectedFolder — current folder object (or null for "All Documents")
 *   onRefresh      — callback after move/delete so parent re-fetches
 */
function DocumentTable({ documents = [], selectedFolder = null, onRefresh }) {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(new Set())
  const [moveModal, setMoveModal] = useState(null) // null | { docs, mode }
  const [deleteConfirm, setDeleteConfirm] = useState(null) // null | [doc, ...]
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  // ── Search filter ────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    if (!search.trim()) return documents
    const q = search.toLowerCase()
    return documents.filter(d =>
      d.fileName?.toLowerCase().includes(q) ||
      d.status?.toLowerCase().includes(q) ||
      d.uploadedBy?.fullName?.toLowerCase().includes(q)
    )
  }, [documents, search])

  // ── Selection helpers ─────────────────────────────────────────────────────────
  const allSelected = filtered.length > 0 && filtered.every(d => selected.has(d.id))
  const someSelected = selected.size > 0

  const toggleAll = () => {
    if (allSelected) {
      setSelected(new Set())
    } else {
      setSelected(new Set(filtered.map(d => d.id)))
    }
  }

  const toggleOne = (id) => {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const selectedDocs = filtered.filter(d => selected.has(d.id))

  // ── Delete ────────────────────────────────────────────────────────────────────
  const handleDeleteConfirmed = async () => {
    if (!deleteConfirm) return
    setBusy(true); setError('')
    try {
      for (const doc of deleteConfirm) {
        await deleteDocument(doc.id)
      }
      setSelected(new Set())
      setDeleteConfirm(null)
      onRefresh?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  // ── Remove from folder ───────────────────────────────────────────────────────
  const handleRemoveFromFolder = async (doc) => {
    if (!selectedFolder) return
    setBusy(true); setError('')
    try {
      await removeDocumentFromFolder(selectedFolder.id, doc.id)
      onRefresh?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const handleBulkRemoveFromFolder = async () => {
    if (!selectedFolder || selectedDocs.length === 0) return
    setBusy(true); setError('')
    try {
      for (const doc of selectedDocs) {
        await removeDocumentFromFolder(selectedFolder.id, doc.id)
      }
      setSelected(new Set())
      onRefresh?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (documents.length === 0 && !search) {
    return (
      <div className="table-container">
        <div className="empty-state">
          <FileText className="empty-state-icon" />
          <h3>No documents yet</h3>
          <p>Upload your first document to get started with AI-powered data extraction.</p>
        </div>
      </div>
    )
  }

  return (
    <div>
      {/* ── Toolbar ─────────────────────────────────────────────────────────── */}
      <div className="doc-table-toolbar">
        <div className="doc-table-search">
          <Search size={15} className="doc-table-search-icon" />
          <input
            type="text"
            placeholder="Search documents…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="doc-table-search-input"
            id="doc-search-input"
          />
        </div>

        {someSelected && (
          <div className="doc-bulk-actions animate-fade-in-up">
            <span className="doc-bulk-count">{selected.size} selected</span>
            <button
              className="doc-bulk-btn"
              onClick={() => setMoveModal({ docs: selectedDocs, mode: 'move' })}
              title="Move to folder"
            >
              <Move size={14} /> Move
            </button>
            {selectedFolder && (
              <button
                className="doc-bulk-btn"
                onClick={handleBulkRemoveFromFolder}
                disabled={busy}
                title="Remove from this folder"
              >
                <FolderMinus size={14} /> Remove from folder
              </button>
            )}
            <button
              className="doc-bulk-btn doc-bulk-btn--danger"
              onClick={() => setDeleteConfirm(selectedDocs)}
              title="Delete selected"
            >
              <Trash2 size={14} /> Delete
            </button>
          </div>
        )}
      </div>

      {error && (
        <div className="upload-status upload-status-error animate-fade-in-up" style={{ marginBottom: '0.75rem' }}>
          {error}
        </div>
      )}

      {/* ── Table ───────────────────────────────────────────────────────────── */}
      <div className="table-container animate-fade-in-up">
        {filtered.length === 0 ? (
          <div className="empty-state">
            <Search className="empty-state-icon" />
            <h3>No results for "{search}"</h3>
            <p>Try a different search term.</p>
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th style={{ width: '36px' }}>
                  <button
                    className="doc-checkbox-btn"
                    onClick={toggleAll}
                    title={allSelected ? 'Deselect all' : 'Select all'}
                  >
                    {allSelected
                      ? <CheckSquare size={15} color="var(--color-accent)" />
                      : <Square size={15} color="var(--color-text-muted)" />}
                  </button>
                </th>
                <th>File Name</th>
                <th>Type</th>
                <th>Status</th>
                <th>Confidence</th>
                <th>Uploaded By</th>
                <th>Date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((doc) => (
                <tr
                  key={doc.id}
                  className={`doc-table-row ${selected.has(doc.id) ? 'doc-table-row--selected' : ''}`}
                >
                  <td>
                    <button
                      className="doc-checkbox-btn"
                      onClick={() => toggleOne(doc.id)}
                    >
                      {selected.has(doc.id)
                        ? <CheckSquare size={15} color="var(--color-accent)" />
                        : <Square size={15} color="var(--color-text-muted)" />}
                    </button>
                  </td>
                  <td>
                    <div className="doc-name-cell">
                      <FileText size={16} strokeWidth={1.8} />
                      <span>{doc.fileName}</span>
                    </div>
                  </td>
                  <td>{doc.fileType}</td>
                  <td><StatusBadge status={doc.status} /></td>
                  <td>
                    {doc.confidenceScore != null
                      ? `${(doc.confidenceScore * 100).toFixed(0)}%`
                      : '—'}
                  </td>
                  <td>{doc.uploadedBy?.fullName || '—'}</td>
                  <td>{doc.createdAt ? new Date(doc.createdAt).toLocaleDateString() : '—'}</td>
                  <td>
                    <div className="doc-row-actions">
                      <button
                        className="doc-action-btn"
                        onClick={() => navigate(`/documents/${doc.id}`)}
                        title="View document"
                      >
                        <Eye size={15} strokeWidth={1.8} />
                      </button>
                      <button
                        className="doc-action-btn"
                        onClick={() => setMoveModal({ docs: [doc], mode: 'move' })}
                        title="Move to folder"
                      >
                        <Move size={15} strokeWidth={1.8} />
                      </button>
                      {selectedFolder && doc.folderId && (
                        <button
                          className="doc-action-btn"
                          onClick={() => handleRemoveFromFolder(doc)}
                          disabled={busy}
                          title="Remove from folder"
                        >
                          <FolderMinus size={15} strokeWidth={1.8} />
                        </button>
                      )}
                      <button
                        className="doc-action-btn doc-action-btn--danger"
                        onClick={() => setDeleteConfirm([doc])}
                        title="Delete document"
                      >
                        <Trash2 size={15} strokeWidth={1.8} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* ── Move / Copy Modal ───────────────────────────────────────────────── */}
      {moveModal && (
        <MoveFolderModal
          docs={moveModal.docs}
          mode={moveModal.mode}
          onDone={() => { setMoveModal(null); setSelected(new Set()); onRefresh?.() }}
          onClose={() => setMoveModal(null)}
        />
      )}

      {/* ── Delete Confirmation Modal ───────────────────────────────────────── */}
      {deleteConfirm && (
        <div className="folder-delete-overlay" onClick={() => setDeleteConfirm(null)}>
          <div className="folder-delete-modal" onClick={e => e.stopPropagation()}>
            <Trash2 size={24} color="#e53e3e" />
            <h4>Delete {deleteConfirm.length === 1 ? 'Document' : `${deleteConfirm.length} Documents`}?</h4>
            <p>
              {deleteConfirm.length === 1
                ? <><strong>{deleteConfirm[0].fileName}</strong> will be permanently removed.</>
                : <>These <strong>{deleteConfirm.length}</strong> documents will be permanently removed.</>}
              {' '}This cannot be undone.
            </p>
            <div className="folder-delete-actions">
              <button
                className="btn-folder-danger"
                onClick={handleDeleteConfirmed}
                disabled={busy}
                id="confirm-delete-doc-btn"
              >
                {busy ? 'Deleting…' : 'Delete'}
              </button>
              <button className="btn-folder-cancel" onClick={() => setDeleteConfirm(null)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default DocumentTable
