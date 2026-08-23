import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle, AlertCircle } from 'lucide-react'
import FileUploader from '../components/documents/FileUploader.jsx'
import { uploadDocuments } from '../services/documentService.js'

function DocumentUploadPage() {
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState(null)   // { message, totalFiles, jobs }
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleUpload = async (files) => {
    setUploading(true)
    setError('')
    setResult(null)

    try {
      const data = await uploadDocuments(files)
      setResult(data)
    } catch (err) {
      setError(err.message || 'Upload failed. Please try again.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Upload Documents</h1>
        <p>Upload invoices, receipts, or scanned documents for AI-powered data extraction.</p>
      </div>

      {/* Upload area — hide once upload succeeded */}
      {!result && (
        <div className="animate-fade-in-up stagger-1">
          <FileUploader onUpload={handleUpload} uploading={uploading} />
        </div>
      )}

      {/* Error message */}
      {error && (
        <div className="upload-status upload-status-error animate-fade-in-up">
          <AlertCircle size={20} />
          <span>{error}</span>
        </div>
      )}

      {/* Success result — show job list */}
      {result && (
        <div className="upload-result animate-fade-in-up">
          <div className="upload-status upload-status-success">
            <CheckCircle size={20} />
            <span>{result.message} — {result.totalFiles} {result.totalFiles === 1 ? 'file' : 'files'} queued</span>
          </div>

          <div className="upload-jobs">
            {result.jobs.map((job) => (
              <div key={job.jobId} className="upload-job-item">
                <div className="upload-job-name">{job.fileName}</div>
                <div className="upload-job-meta">
                  <span className="status-badge status-pending">{job.status}</span>
                  <span className="upload-job-id">ID: {job.jobId.slice(0, 8)}…</span>
                </div>
              </div>
            ))}
          </div>

          <div className="upload-actions">
            <button className="btn btn-secondary" onClick={() => setResult(null)}>
              Upload More
            </button>
            <button className="btn btn-primary" onClick={() => navigate('/documents')}>
              View Documents
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default DocumentUploadPage
