import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Folder, Files, X, ArrowRight, Copy } from 'lucide-react'
import { getFolders, addDocumentToFolder, removeDocumentFromFolder } from '../../services/folderService.js'
import '../../styles/folders.css'

/**
 * MoveFolderModal
 *
 * Props:
 *   docs         — array of { id, fileName, folderId } to move/copy
 *   mode         — 'move' | 'copy'  (copy just moves, backend has no separate copy — for UI clarity)
 *   onDone(updatedDocs) — called after successful operation
 *   onClose      — called to dismiss modal
 */
function MoveFolderModal({ docs = [], mode = 'move', onDone, onClose }) {
  const [folders, setFolders] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [targetFolder, setTargetFolder] = useState(undefined) // undefined = not chosen yet

  useEffect(() => {
    getFolders()
      .then(setFolders)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  const handleConfirm = async () => {
    if (targetFolder === undefined) { setError('Please choose a destination'); return }
    setBusy(true); setError('')
    try {
      for (const doc of docs) {
        if (targetFolder === null) {
          // Move to "All Documents" (remove from current folder)
          if (doc.folderId) {
            await removeDocumentFromFolder(doc.folderId, doc.id)
          }
        } else {
          await addDocumentToFolder(targetFolder.id, doc.id)
        }
      }
      onDone?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const title = mode === 'copy' ? 'Copy to Folder' : 'Move to Folder'
  const actionLabel = mode === 'copy' ? 'Copy' : 'Move'
  const Icon = mode === 'copy' ? Copy : ArrowRight

  return createPortal(
    <div className="folder-delete-overlay" onClick={onClose}>
      <div
        className="move-folder-modal"
        onClick={e => e.stopPropagation()}
      >
        <div className="move-folder-modal-header">
          <h4>{title}</h4>
          <button className="folder-action-icon" onClick={onClose}><X size={16} /></button>
        </div>

        <p className="move-folder-modal-sub">
          {docs.length === 1
            ? <>Moving <strong>{docs[0].fileName}</strong></>
            : <>Moving <strong>{docs.length}</strong> documents</>}
        </p>

        {error && <div className="folder-error-banner" style={{ marginBottom: '0.75rem' }}>{error}</div>}

        {loading ? (
          <div className="folder-loading">Loading folders…</div>
        ) : (
          <div className="move-folder-list">
            {/* "All Documents" (no folder) option */}
            <button
              className={`move-folder-option ${targetFolder === null ? 'move-folder-option--active' : ''}`}
              onClick={() => setTargetFolder(null)}
            >
              <div className="folder-item-icon" style={{ background: 'var(--color-accent-light)' }}>
                <Files size={14} color="var(--color-accent)" />
              </div>
              <span>All Documents (unorganized)</span>
            </button>

            {folders.map(f => (
              <button
                key={f.id}
                className={`move-folder-option ${targetFolder?.id === f.id ? 'move-folder-option--active' : ''}`}
                onClick={() => setTargetFolder(f)}
              >
                <div className="folder-item-icon" style={{ background: f.color + '22' }}>
                  <Folder size={14} color={f.color} />
                </div>
                <span>{f.name}</span>
                <span className="folder-item-count">{f.documentCount} files</span>
              </button>
            ))}
          </div>
        )}

        <div className="folder-delete-actions" style={{ marginTop: '1rem' }}>
          <button
            className="btn-folder-save"
            onClick={handleConfirm}
            disabled={busy || loading || targetFolder === undefined}
            id="confirm-move-btn"
          >
            <Icon size={14} />
            {busy ? `${actionLabel}ing…` : `${actionLabel} Here`}
          </button>
          <button className="btn-folder-cancel" onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>,
    document.body
  )
}

export default MoveFolderModal
