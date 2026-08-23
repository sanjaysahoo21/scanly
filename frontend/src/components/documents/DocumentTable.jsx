import { FileText, Eye } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import StatusBadge from '../common/StatusBadge.jsx'
import '../../styles/documents.css'

function DocumentTable({ documents = [] }) {
  const navigate = useNavigate()

  if (documents.length === 0) {
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
    <div className="table-container animate-fade-in-up">
      <table>
        <thead>
          <tr>
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
          {documents.map((doc) => (
            <tr key={doc.id} className="doc-table-row">
              <td>
                <div className="doc-name-cell">
                  <FileText size={16} strokeWidth={1.8} />
                  <span>{doc.fileName}</span>
                </div>
              </td>
              <td>{doc.fileType}</td>
              <td>
                <StatusBadge status={doc.status} />
              </td>
              <td>
                {doc.confidenceScore != null
                  ? `${(doc.confidenceScore * 100).toFixed(0)}%`
                  : '—'}
              </td>
              <td>{doc.uploadedBy?.fullName || '—'}</td>
              <td>{doc.createdAt ? new Date(doc.createdAt).toLocaleDateString() : '—'}</td>
              <td>
                <button
                  className="doc-action-btn"
                  onClick={() => navigate(`/documents/${doc.id}`)}
                  aria-label="View document"
                >
                  <Eye size={16} strokeWidth={1.8} />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default DocumentTable
