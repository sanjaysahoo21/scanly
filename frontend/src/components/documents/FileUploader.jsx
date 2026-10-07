import { Upload, X, FileText, Image, Folder, FolderOpen, Plus, Search } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import Button from '../common/Button.jsx'
import '../../styles/documents.css'
import '../../styles/folders.css'

const ACCEPTED_TYPES = ['.pdf', '.jpg', '.jpeg', '.png', '.webp']
const ACCEPTED_MIME  = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
const MAX_SIZE_BYTES = 10 * 1024 * 1024 // 10MB

// ── helpers ──────────────────────────────────────────────────────────────────
function validateFile(file) {
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  if (!ACCEPTED_TYPES.includes(ext)) {
    return `Unsupported type (${ext}). Use PDF, JPG, PNG, or WEBP.`
  }
  if (file.size > MAX_SIZE_BYTES) {
    return 'File exceeds 10MB limit'
  }
  return null
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function getFileIcon(fileName) {
  const ext = fileName.split('.').pop().toLowerCase()
  return ['jpg', 'jpeg', 'png', 'webp'].includes(ext) ? Image : FileText
}

function isImageFile(fileName) {
  const ext = fileName.split('.').pop().toLowerCase()
  return ['jpg', 'jpeg', 'png', 'webp'].includes(ext)
}

/**
 * FileUploader component.
 *
 * Props:
 *   onUpload(files, folderId, mode)  — called with the validated files array,
 *                                      selected folderId (may be null), and mode ('files'|'folder')
 *   uploading {boolean}
 *   folders {Array}                 — list of FolderResponse objects for the selector dropdown
 */
function FileUploader({ onUpload, onCreateFolder, uploading = false, folders = [] }) {
  const [mode, setMode] = useState('files')  // 'files' | 'folder'
  const [files, setFiles] = useState([])
  const [dragActive, setDragActive] = useState(false)
  const [selectedFolderId, setSelectedFolderId] = useState('')
  const [folderSearch, setFolderSearch] = useState('')
  const [newFolderName, setNewFolderName] = useState('')
  const [creatingFolder, setCreatingFolder] = useState(false)
  const [folderError, setFolderError] = useState('')

  // Folder upload specific
  const [folderName, setFolderName] = useState('')
  const [skippedCount, setSkippedCount] = useState(0)

  const fileInputRef = useRef(null)
  const folderInputRef = useRef(null)

  // Reset files when switching mode
  useEffect(() => {
    setFiles([])
    setFolderName('')
    setSkippedCount(0)
  }, [mode])

  // ── File mode helpers ────────────────────────────────────────────────────────

  const addFiles = (newFiles) => {
    const fileList = Array.from(newFiles).map((file) => ({
      file,
      id: crypto.randomUUID(),
      error: validateFile(file),
    }))
    setFiles((prev) => [...prev, ...fileList])
  }

  // ── Folder mode helpers ──────────────────────────────────────────────────────

  const addFolderFiles = (newFiles) => {
    const all = Array.from(newFiles)
    let skipped = 0
    const valid = all
      .filter(f => {
        // Skip hidden files (start with .) and directories
        const name = f.name.split('/').pop()
        if (name.startsWith('.')) { skipped++; return false }
        const err = validateFile(f)
        if (err) { skipped++; return false }
        return true
      })
      .map(file => ({
        file,
        id: crypto.randomUUID(),
        error: null,
        // Preserve the relative path within the folder
        relativePath: file.webkitRelativePath || file.name,
      }))

    // Infer folder name from first file's relative path
    if (valid.length > 0 && valid[0].relativePath.includes('/')) {
      setFolderName(valid[0].relativePath.split('/')[0])
    }
    setSkippedCount(skipped)
    setFiles(valid)
  }

  // ── Drag & drop ──────────────────────────────────────────────────────────────

  const handleDrop = (e) => {
    e.preventDefault()
    setDragActive(false)
    if (mode === 'files' && e.dataTransfer.files.length > 0) {
      addFiles(e.dataTransfer.files)
    }
  }

  const handleDragOver = (e) => { e.preventDefault(); setDragActive(true) }
  const handleDragLeave = () => setDragActive(false)

  // ── Input change handlers ────────────────────────────────────────────────────

  const handleFileInputChange = (e) => {
    if (e.target.files.length > 0) {
      addFiles(e.target.files)
      e.target.value = ''
    }
  }

  const handleFolderInputChange = (e) => {
    if (e.target.files.length > 0) {
      addFolderFiles(e.target.files)
      e.target.value = ''
    }
  }

  const removeFile = (id) => {
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }

  const validFiles = files.filter((f) => !f.error)
  const normalizedFolderSearch = folderSearch.trim().toLocaleLowerCase()
  const filteredFolders = [...folders].sort((first, second) => {
    if (!normalizedFolderSearch) return first.name.localeCompare(second.name)

    const firstName = first.name.toLocaleLowerCase()
    const secondName = second.name.toLocaleLowerCase()
    const firstRank = firstName.startsWith(normalizedFolderSearch) ? 0 : firstName.includes(normalizedFolderSearch) ? 1 : 2
    const secondRank = secondName.startsWith(normalizedFolderSearch) ? 0 : secondName.includes(normalizedFolderSearch) ? 1 : 2
    return firstRank - secondRank || firstName.localeCompare(secondName)
  })

  const selectFolder = (folder) => {
    setSelectedFolderId(folder?.id || '')
    setFolderSearch(folder?.name || '')
    setFolderError('')
  }

  const createFolder = async () => {
    const name = newFolderName.trim()
    if (!name) {
      setFolderError('Enter a folder name to create one.')
      return
    }
    if (!onCreateFolder) return

    setCreatingFolder(true)
    setFolderError('')
    try {
      const folder = await onCreateFolder(name)
      selectFolder(folder)
      setNewFolderName('')
    } catch (error) {
      setFolderError(error.message || 'Could not create folder.')
    } finally {
      setCreatingFolder(false)
    }
  }

  const handleUpload = () => {
    if (onUpload && validFiles.length > 0) {
      onUpload(
        validFiles.map((f) => f.file),
        selectedFolderId || null,
        mode,
        folderName,
      )
    }
  }

  const dropZoneClick = () => {
    if (mode === 'files') fileInputRef.current?.click()
    else folderInputRef.current?.click()
  }

  return (
    <div className="file-uploader">

      {/* ── Mode tabs ───────────────────────────────────────────────────── */}
      <div className="upload-mode-tabs">
        <button
          className={`upload-mode-tab ${mode === 'files' ? 'upload-mode-tab--active' : ''}`}
          onClick={() => setMode('files')}
          id="mode-tab-files"
          type="button"
        >
          <FileText size={14} />
          Upload Files
        </button>
        <button
          className={`upload-mode-tab ${mode === 'folder' ? 'upload-mode-tab--active' : ''}`}
          onClick={() => setMode('folder')}
          id="mode-tab-folder"
          type="button"
        >
          <Folder size={14} />
          Upload Folder
        </button>
      </div>

      {/* ── Folder destination selector ──────────────────────────────────── */}
      <section className="folder-selector" aria-label="Folder destination">
        <div className="folder-selector-heading">
          <span className="folder-selector-label"><Folder size={14} /> Save to folder</span>
          {selectedFolderId && (
            <button type="button" className="folder-clear-selection" onClick={() => selectFolder(null)}>
              Clear selection
            </button>
          )}
        </div>
        <div className="folder-search-field">
          <Search size={15} aria-hidden="true" />
          <input
            id="folder-dest-search"
            value={folderSearch}
            onChange={(event) => {
              setFolderSearch(event.target.value)
              if (selectedFolderId) setSelectedFolderId('')
            }}
            placeholder={folders.length ? 'Search folders…' : 'No folders yet'}
            aria-label="Search folders"
          />
        </div>
        <select
          className="folder-destination-select"
          value={selectedFolderId}
          onChange={(event) => selectFolder(folders.find((folder) => folder.id === event.target.value) || null)}
          aria-label="Select destination folder"
        >
          <option value="">No folder (unorganized)</option>
          {filteredFolders.map((folder) => (
            <option key={folder.id} value={folder.id}>{folder.name}</option>
          ))}
        </select>
        <div className="folder-create-inline">
          <input
            value={newFolderName}
            onChange={(event) => setNewFolderName(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && createFolder()}
            placeholder="Create a new folder"
            aria-label="New folder name"
          />
          <button type="button" onClick={createFolder} disabled={creatingFolder}>
            <Plus size={15} /> {creatingFolder ? 'Creating…' : 'Create'}
          </button>
        </div>
        {folderError && <p className="folder-picker-error">{folderError}</p>}
        {mode === 'folder' && !selectedFolderId && (
          <p className="folder-auto-create-note">
            A folder named after the uploaded folder will be created automatically.
          </p>
        )}
      </section>

      {/* ── Drop Zone ────────────────────────────────────────────────────── */}
      <div
        className={`drop-zone ${dragActive ? 'drop-zone-active' : ''} ${mode === 'folder' ? 'drop-zone--folder' : ''}`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={dropZoneClick}
        role="button"
        tabIndex={0}
        onKeyDown={e => e.key === 'Enter' && dropZoneClick()}
        id="file-drop-zone"
      >
        {/* Hidden file inputs */}
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept=".pdf,application/pdf,.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
          onChange={handleFileInputChange}
          className="drop-zone-input"
          id="file-input"
        />
        <input
          ref={folderInputRef}
          type="file"
          // @ts-ignore — webkitdirectory is non-standard but widely supported
          webkitdirectory=""
          multiple
          accept=".pdf,application/pdf,.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
          onChange={handleFolderInputChange}
          className="drop-zone-input"
          id="folder-input"
        />

        <div className="drop-zone-icon">
          {mode === 'folder'
            ? <FolderOpen size={32} strokeWidth={1.5} />
            : <Upload size={32} strokeWidth={1.5} />
          }
        </div>

        <div className="drop-zone-text">
          {mode === 'folder' ? (
            <>
              <span className="drop-zone-title">Click to select a folder</span>
              <span className="drop-zone-subtitle">
                Files are uploaded in safe batches and <span className="drop-zone-link">queued for processing</span>
              </span>
            </>
          ) : (
            <>
              <span className="drop-zone-title">Drag &amp; drop files here</span>
              <span className="drop-zone-subtitle">
                or <span className="drop-zone-link">click to browse</span>
              </span>
            </>
          )}
        </div>

        <div className="drop-zone-type-chips">
          <span className="type-chip type-chip--pdf">PDF</span>
          <span className="type-chip type-chip--image">JPG</span>
          <span className="type-chip type-chip--image">PNG</span>
          <span className="type-chip type-chip--image">WEBP</span>
        </div>

        <span className="drop-zone-hint">
          {mode === 'folder'
            ? 'Non-invoice files are automatically skipped'
            : 'Max 10MB per file'}
        </span>
      </div>

      {/* ── Folder name banner ───────────────────────────────────────────── */}
      {mode === 'folder' && folderName && files.length > 0 && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          padding: 'var(--space-2) var(--space-4)',
          background: 'var(--color-accent-light)',
          borderRadius: 'var(--radius-md)',
          fontSize: 'var(--text-sm)',
          color: 'var(--color-accent)',
          fontWeight: 'var(--weight-medium)',
        }}>
          <FolderOpen size={15} />
          <strong>{folderName}</strong>
          &nbsp;— {validFiles.length} valid file{validFiles.length !== 1 ? 's' : ''}
          {skippedCount > 0 && (
            <span style={{ color: 'var(--color-text-muted)', fontWeight: 'normal', marginLeft: 4 }}>
              ({skippedCount} skipped)
            </span>
          )}
        </div>
      )}

      {/* ── File List ────────────────────────────────────────────────────── */}
      {files.length > 0 && (
        <div className="file-list animate-fade-in-up">
          <div className="file-list-header">
            <h4>
              {mode === 'folder'
                ? `Folder Contents — ${files.length} file${files.length !== 1 ? 's' : ''} ready`
                : `Selected Files (${files.length})`}
            </h4>
          </div>

          {files.map((item, index) => {
            const FileIcon = getFileIcon(item.file.name)
            const isImg = isImageFile(item.file.name)
            return (
              <div
                key={item.id}
                className={`file-item animate-fade-in-up stagger-${Math.min(index + 1, 5)} ${item.error ? 'file-item-error' : ''}`}
              >
                <div className={`file-item-icon ${isImg ? 'file-item-icon--image' : ''}`}>
                  <FileIcon size={18} strokeWidth={1.8} />
                </div>
                <div className="file-item-info">
                  <span className="file-item-name" title={item.relativePath || item.file.name}>
                    {item.relativePath
                      ? item.relativePath.split('/').slice(1).join('/') || item.file.name
                      : item.file.name}
                  </span>
                  <span className="file-item-size">
                    {isImg ? (
                      <span className="file-item-badge file-item-badge--image">Image · {formatSize(item.file.size)}</span>
                    ) : (
                      <span className="file-item-badge file-item-badge--pdf">PDF · {formatSize(item.file.size)}</span>
                    )}
                  </span>
                </div>
                {item.error && (
                  <span className="file-item-error-text">{item.error}</span>
                )}
                {mode === 'files' && (
                  <button
                    className="file-item-remove"
                    onClick={(e) => { e.stopPropagation(); removeFile(item.id) }}
                    aria-label="Remove file"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>
            )
          })}

          {validFiles.length > 0 && (
            <div className="file-list-actions">
              <Button
                variant="primary"
                icon={mode === 'folder' ? Folder : Upload}
                onClick={handleUpload}
                id="upload-button"
                disabled={uploading}
              >
                {uploading
                  ? 'Uploading…'
                  : mode === 'folder'
                    ? `Validate ${validFiles.length} File${validFiles.length !== 1 ? 's' : ''} in Folder`
                    : `Upload ${validFiles.length} ${validFiles.length === 1 ? 'File' : 'Files'}`
                }
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default FileUploader
