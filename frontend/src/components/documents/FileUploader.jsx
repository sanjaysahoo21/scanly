import { Upload, X, FileText, Image } from 'lucide-react'
import { useState, useRef } from 'react'
import Button from '../common/Button.jsx'
import '../../styles/documents.css'

const ACCEPTED_TYPES = ['.pdf', '.jpg', '.jpeg', '.png']
const MAX_SIZE_BYTES = 10 * 1024 * 1024 // 10MB

function FileUploader({ onUpload, uploading = false }) {
  const [files, setFiles] = useState([])
  const [dragActive, setDragActive] = useState(false)
  const inputRef = useRef(null)

  const validateFile = (file) => {
    const ext = '.' + file.name.split('.').pop().toLowerCase()
    if (!ACCEPTED_TYPES.includes(ext)) {
      return 'Invalid file type'
    }
    if (file.size > MAX_SIZE_BYTES) {
      return 'File exceeds 10MB limit'
    }
    return null
  }

  const addFiles = (newFiles) => {
    const fileList = Array.from(newFiles).map((file) => ({
      file,
      id: crypto.randomUUID(),
      error: validateFile(file),
    }))
    setFiles((prev) => [...prev, ...fileList])
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragActive(false)
    if (e.dataTransfer.files.length > 0) {
      addFiles(e.dataTransfer.files)
    }
  }

  const handleDragOver = (e) => {
    e.preventDefault()
    setDragActive(true)
  }

  const handleDragLeave = () => {
    setDragActive(false)
  }

  const handleInputChange = (e) => {
    if (e.target.files.length > 0) {
      addFiles(e.target.files)
      e.target.value = ''
    }
  }

  const removeFile = (id) => {
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }

  const validFiles = files.filter((f) => !f.error)

  const handleUpload = () => {
    if (onUpload && validFiles.length > 0) {
      onUpload(validFiles.map((f) => f.file))
    }
  }

  const getFileIcon = (fileName) => {
    const ext = fileName.split('.').pop().toLowerCase()
    if (['jpg', 'jpeg', 'png'].includes(ext)) return Image
    return FileText
  }

  const formatSize = (bytes) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  return (
    <div className="file-uploader">
      {/* Drop Zone */}
      <div
        className={`drop-zone ${dragActive ? 'drop-zone-active' : ''}`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={() => inputRef.current?.click()}
        role="button"
        tabIndex={0}
        id="file-drop-zone"
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept=".pdf,.jpg,.jpeg,.png"
          onChange={handleInputChange}
          className="drop-zone-input"
          id="file-input"
        />
        <div className="drop-zone-icon">
          <Upload size={32} strokeWidth={1.5} />
        </div>
        <div className="drop-zone-text">
          <span className="drop-zone-title">Drag & drop files here</span>
          <span className="drop-zone-subtitle">
            or <span className="drop-zone-link">click to browse</span>
          </span>
        </div>
        <span className="drop-zone-hint">
          PDF, JPG, PNG — Max 10MB per file
        </span>
      </div>

      {/* File List */}
      {files.length > 0 && (
        <div className="file-list animate-fade-in-up">
          <div className="file-list-header">
            <h4>Selected Files ({files.length})</h4>
          </div>
          {files.map((item, index) => {
            const FileIcon = getFileIcon(item.file.name)
            return (
              <div
                key={item.id}
                className={`file-item animate-fade-in-up stagger-${index + 1} ${item.error ? 'file-item-error' : ''}`}
              >
                <div className="file-item-icon">
                  <FileIcon size={18} strokeWidth={1.8} />
                </div>
                <div className="file-item-info">
                  <span className="file-item-name">{item.file.name}</span>
                  <span className="file-item-size">{formatSize(item.file.size)}</span>
                </div>
                {item.error && (
                  <span className="file-item-error-text">{item.error}</span>
                )}
                <button
                  className="file-item-remove"
                  onClick={(e) => {
                    e.stopPropagation()
                    removeFile(item.id)
                  }}
                  aria-label="Remove file"
                >
                  <X size={14} />
                </button>
              </div>
            )
          })}

          {validFiles.length > 0 && (
            <div className="file-list-actions">
              <Button
                variant="primary"
                icon={Upload}
                onClick={handleUpload}
                id="upload-button"
                disabled={uploading}
              >
                {uploading ? 'Uploading...' : `Upload ${validFiles.length} ${validFiles.length === 1 ? 'File' : 'Files'}`}
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default FileUploader
