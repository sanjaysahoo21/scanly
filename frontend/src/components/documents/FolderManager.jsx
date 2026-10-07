import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Folder, FolderPlus, Edit2, Trash2, X, AlertCircle, ChevronRight, Files, Search, ChevronDown, ArrowUpDown } from 'lucide-react'
import { getFolders, createFolder, updateFolder, deleteFolder } from '../../services/folderService.js'
import '../../styles/folders.css'

const PRESET_COLORS = [
  '#6366f1', '#8b5cf6', '#ec4899', '#f43f5e',
  '#f97316', '#eab308', '#22c55e', '#14b8a6', '#0ea5e9', '#6b7280',
]

const TOP_N = 5 // folders to show before "show more"

const FOLDER_SORT_OPTIONS = [
  { value: 'recent',   label: 'Recent' },
  { value: 'oldest',   label: 'Oldest' },
  { value: 'name_asc', label: 'A → Z' },
  { value: 'name_desc',label: 'Z → A' },
  { value: 'most',     label: 'Most files' },
]

function sortFolders(folders, key) {
  const arr = [...folders]
  switch (key) {
    case 'oldest':   return arr.sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt))
    case 'name_asc': return arr.sort((a,b) => a.name.localeCompare(b.name))
    case 'name_desc':return arr.sort((a,b) => b.name.localeCompare(a.name))
    case 'most':     return arr.sort((a,b) => (b.documentCount||0) - (a.documentCount||0))
    default:         return arr.sort((a,b) => new Date(b.createdAt) - new Date(a.createdAt))
  }
}

/**
 * FolderManager — full folder CRUD UI with search + top-5 collapsing.
 *
 * Props:
 *   onSelectFolder(folder | null)
 *   selectedFolderId
 *   compact
 *   variant
 *   onFoldersChange(folders)  — optional, called after create/update/delete
 */
function FolderManager({ onSelectFolder, selectedFolderId, compact = false, variant = 'sidebar', onFoldersChange }) {
  const [folders, setFolders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [showAll, setShowAll] = useState(false)
  const [folderSort, setFolderSort] = useState('recent')

  // Create dialog state
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createForm, setCreateForm] = useState({ name: '', description: '', color: '#6366f1' })
  const [createError, setCreateError] = useState('')

  // Edit-in-place state
  const [editingId, setEditingId] = useState(null)
  const [editForm, setEditForm] = useState({ name: '', description: '', color: '' })
  const [editError, setEditError] = useState('')
  const [saving, setSaving] = useState(false)

  // Delete confirmation
  const [deletingId, setDeletingId] = useState(null)
  const [deleting, setDeleting] = useState(false)

  const nameInputRef = useRef(null)

  const load = async () => {
    setLoading(true); setError('')
    try {
      const data = await getFolders()
      setFolders(data)
      onFoldersChange?.(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])
  useEffect(() => { if (showCreate && nameInputRef.current) nameInputRef.current.focus() }, [showCreate])

  // ── Derived folder list (filtered + sorted + truncated) ──────────────────────
  const q = search.trim().toLowerCase()
  const matchedFolders = sortFolders(
    q ? folders.filter(f => f.name.toLowerCase().includes(q)) : folders,
    folderSort
  )
  const visibleFolders = (q || showAll) ? matchedFolders : matchedFolders.slice(0, TOP_N)
  const hasMore = !q && !showAll && matchedFolders.length > TOP_N

  // ── Create ────────────────────────────────────────────────────────────────────
  const handleCreate = async (e) => {
    e.preventDefault()
    if (!createForm.name.trim()) { setCreateError('Name is required'); return }
    setCreating(true); setCreateError('')
    try {
      const folder = await createFolder(createForm)
      const updated = [folder, ...folders]
      setFolders(updated)
      onFoldersChange?.(updated)
      setShowCreate(false)
      setCreateForm({ name: '', description: '', color: '#6366f1' })
    } catch (err) {
      setCreateError(err.message)
    } finally {
      setCreating(false)
    }
  }

  // ── Edit ──────────────────────────────────────────────────────────────────────
  const startEdit = (folder, e) => {
    e.stopPropagation()
    setEditingId(folder.id)
    setEditForm({ name: folder.name, description: folder.description || '', color: folder.color || '#6366f1' })
    setEditError('')
  }

  const saveEdit = async (e) => {
    e.preventDefault()
    if (!editForm.name.trim()) { setEditError('Name is required'); return }
    setSaving(true); setEditError('')
    try {
      const updated = await updateFolder(editingId, editForm)
      const next = folders.map(f => f.id === editingId ? updated : f)
      setFolders(next)
      onFoldersChange?.(next)
      if (selectedFolderId === editingId) onSelectFolder?.(updated)
      setEditingId(null)
    } catch (err) {
      setEditError(err.message)
    } finally {
      setSaving(false)
    }
  }

  // ── Delete ─────────────────────────────────────────────────────────────────────
  const confirmDelete = async () => {
    if (!deletingId) return
    setDeleting(true)
    try {
      await deleteFolder(deletingId)
      const next = folders.filter(f => f.id !== deletingId)
      setFolders(next)
      onFoldersChange?.(next)
      if (selectedFolderId === deletingId) onSelectFolder?.(null)
      setDeletingId(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setDeleting(false)
    }
  }

  if (loading) return <div className="folder-loading">Loading folders…</div>

  return (
    <div className={`folder-manager ${compact ? 'folder-manager--compact' : ''} folder-manager--${variant}`}>

      {error && (
        <div className="folder-error-banner"><AlertCircle size={15} /> {error}</div>
      )}

      {/* Header */}
      <div className="folder-manager-header">
        <span className="folder-manager-title">
          <Folder size={15} /> Folders
        </span>
        <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
          {/* Folder sort */}
          <select
            className="folder-sort-select"
            value={folderSort}
            onChange={e => setFolderSort(e.target.value)}
            title="Sort folders"
            id="folder-sort-select"
          >
            {FOLDER_SORT_OPTIONS.map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <button
            className="folder-add-btn"
            onClick={() => setShowCreate(v => !v)}
            title="Create folder"
            id="create-folder-btn"
          >
            <FolderPlus size={15} />
            {!compact && <span>New</span>}
          </button>
        </div>
      </div>

      {/* Search input */}
      <div className="folder-search-row">
        <Search size={13} className="folder-search-icon" />
        <input
          type="text"
          className="folder-search-input"
          placeholder="Search folders…"
          value={search}
          onChange={e => { setSearch(e.target.value); setShowAll(true) }}
          id="folder-search-input"
        />
        {search && (
          <button className="folder-search-clear" onClick={() => { setSearch(''); setShowAll(false) }}>
            <X size={12} />
          </button>
        )}
      </div>

      {/* Create form */}
      {showCreate && (
        <form className="folder-create-form animate-fade-in-up" onSubmit={handleCreate}>
          <div className="folder-create-row">
            <input
              ref={nameInputRef}
              className="folder-input"
              placeholder="Folder name…"
              value={createForm.name}
              onChange={e => setCreateForm(p => ({ ...p, name: e.target.value }))}
              id="new-folder-name"
            />
            <div className="folder-color-picker">
              {PRESET_COLORS.map(c => (
                <button
                  key={c} type="button"
                  className={`folder-color-dot ${createForm.color === c ? 'folder-color-dot--active' : ''}`}
                  style={{ background: c }}
                  onClick={() => setCreateForm(p => ({ ...p, color: c }))}
                  title={c}
                />
              ))}
            </div>
          </div>
          <input
            className="folder-input"
            placeholder="Description (optional)"
            value={createForm.description}
            onChange={e => setCreateForm(p => ({ ...p, description: e.target.value }))}
            id="new-folder-desc"
          />
          {createError && <span className="folder-field-error">{createError}</span>}
          <div className="folder-create-actions">
            <button type="submit" className="btn-folder-save" disabled={creating} id="save-folder-btn">
              {creating ? 'Creating…' : 'Create Folder'}
            </button>
            <button type="button" className="btn-folder-cancel"
              onClick={() => { setShowCreate(false); setCreateError('') }}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* "All Documents" */}
      <button
        className={`folder-item ${!selectedFolderId ? 'folder-item--active' : ''}`}
        onClick={() => onSelectFolder?.(null)}
        id="folder-all"
      >
        <div className="folder-item-icon" style={{ background: 'var(--color-accent-light)' }}>
          <Files size={14} color="var(--color-accent)" />
        </div>
        <div className="folder-item-info">
          <span className="folder-item-name">All Documents</span>
        </div>
        <ChevronRight size={13} className="folder-item-arrow" />
      </button>

      {/* Folder list */}
      {matchedFolders.length === 0 && !showCreate && (
        <div className="folder-empty">
          <span>{search ? 'No folders match your search' : 'No folders yet'}</span>
        </div>
      )}

      {visibleFolders.map(folder => (
        <div key={folder.id} className="folder-item-wrapper">
          {editingId === folder.id ? (
            <form className="folder-edit-form" onSubmit={saveEdit}>
              <div className="folder-create-row">
                <input
                  className="folder-input"
                  value={editForm.name}
                  onChange={e => setEditForm(p => ({ ...p, name: e.target.value }))}
                  autoFocus
                />
                <div className="folder-color-picker">
                  {PRESET_COLORS.map(c => (
                    <button
                      key={c} type="button"
                      className={`folder-color-dot ${editForm.color === c ? 'folder-color-dot--active' : ''}`}
                      style={{ background: c }}
                      onClick={() => setEditForm(p => ({ ...p, color: c }))}
                    />
                  ))}
                </div>
              </div>
              <input
                className="folder-input"
                placeholder="Description (optional)"
                value={editForm.description}
                onChange={e => setEditForm(p => ({ ...p, description: e.target.value }))}
              />
              {editError && <span className="folder-field-error">{editError}</span>}
              <div className="folder-create-actions">
                <button type="submit" className="btn-folder-save" disabled={saving}>
                  {saving ? 'Saving…' : 'Save'}
                </button>
                <button type="button" className="btn-folder-cancel" onClick={() => setEditingId(null)}>
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <button
              className={`folder-item ${selectedFolderId === folder.id ? 'folder-item--active' : ''}`}
              onClick={() => onSelectFolder?.(folder)}
              id={`folder-${folder.id}`}
            >
              <div className="folder-item-icon" style={{ background: folder.color + '22' }}>
                <Folder size={14} color={folder.color} />
              </div>
              <div className="folder-item-info">
                <span className="folder-item-name">{folder.name}</span>
                <span className="folder-item-count">
                  {folder.documentCount} {folder.documentCount === 1 ? 'file' : 'files'}
                </span>
              </div>
              <div className="folder-item-actions" onClick={e => e.stopPropagation()}>
                <button className="folder-action-icon" title="Edit folder"
                  onClick={e => startEdit(folder, e)} id={`edit-folder-${folder.id}`}>
                  <Edit2 size={12} />
                </button>
                <button className="folder-action-icon folder-action-icon--danger" title="Delete folder"
                  onClick={e => { e.stopPropagation(); setDeletingId(folder.id) }}
                  id={`delete-folder-${folder.id}`}>
                  <Trash2 size={12} />
                </button>
              </div>
              <ChevronRight size={13} className="folder-item-arrow" />
            </button>
          )}
        </div>
      ))}

      {/* Show more / less toggle */}
      {hasMore && (
        <button className="folder-show-more" onClick={() => setShowAll(true)}>
          <ChevronDown size={13} />
          Show {matchedFolders.length - TOP_N} more…
        </button>
      )}
      {!hasMore && showAll && matchedFolders.length > TOP_N && !search && (
        <button className="folder-show-more" onClick={() => setShowAll(false)}>
          Show less
        </button>
      )}

      {/* Delete confirmation modal */}
      {deletingId && createPortal(
        <div className="folder-delete-overlay" onClick={() => setDeletingId(null)}>
          <div className="folder-delete-modal" onClick={e => e.stopPropagation()}>
            <Trash2 size={24} color="#e53e3e" />
            <h4>Delete Folder?</h4>
            <p>Documents inside will become <strong>unorganized</strong> but won't be deleted.</p>
            <div className="folder-delete-actions">
              <button className="btn-folder-danger" onClick={confirmDelete} disabled={deleting}
                id="confirm-delete-folder-btn">
                {deleting ? 'Deleting…' : 'Delete Folder'}
              </button>
              <button className="btn-folder-cancel" onClick={() => setDeletingId(null)}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}

export default FolderManager
