import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle, AlertCircle, Loader } from 'lucide-react'
import FileUploader from '../components/documents/FileUploader.jsx'
import { uploadDocuments } from '../services/documentService.js'

function DocumentUploadPage() {
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState(null)   // { message, totalFiles, jobs }
  const [error, setError] = useState('')
  const [countdown, setCountdown] = useState(3)
  const navigate = useNavigate()

  // Auto-redirect to /documents after success so the user sees live status
  useEffect(() => {
    if (!result) return
    const timer = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(timer)
          navigate('/documents')
        }
        return c - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [result, navigate])

  const handleUpload = async (files) => {
    setUploading(true)
    setError('')
    setResult(null)
    setCountdown(3)

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

      {/* Success result — show confirmation then redirect */}
      {result && (
        <div className="upload-result animate-fade-in-up">
          <div className="upload-status upload-status-success">
            <CheckCircle size={20} />
            <span>
              {result.totalFiles} {result.totalFiles === 1 ? 'file' : 'files'} uploaded and queued for AI processing!
            </span>
          </div>

          {/* File list */}
          <div className="upload-jobs">
            {result.jobs.map((job) => (
              <div key={job.jobId} className="upload-job-item">
                <div className="upload-job-name">{job.fileName}</div>
                <div className="upload-job-meta">
                  <span className="status-badge status-processing" style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                    <Loader size={12} style={{ animation: 'spin 1s linear infinite' }} />
                    Processing…
                  </span>
                </div>
              </div>
            ))}
          </div>

          {/* Auto-redirect notice */}
          <div style={{
            marginTop: '1.5rem',
            padding: '1rem',
            background: 'var(--color-surface)',
            borderRadius: 'var(--radius-md)',
            color: 'var(--color-text-muted)',
            fontSize: 'var(--text-sm)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem'
          }}>
            <Loader size={14} style={{ animation: 'spin 1s linear infinite', flexShrink: 0 }} />
            Redirecting to Documents in {countdown}s to track live status…
          </div>

          <div className="upload-actions" style={{ marginTop: '1rem' }}>
            <button className="btn btn-secondary" onClick={() => { setResult(null); setError('') }}>
              Upload More
            </button>
            <button className="btn btn-primary" onClick={() => navigate('/documents')}>
              View Documents Now
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default DocumentUploadPage
