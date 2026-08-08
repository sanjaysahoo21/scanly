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
                  <span>{doc.file_name}</span>
                </div>
              </td>
              <td>{doc.file_type}</td>
              <td>
                <StatusBadge status={doc.status} />
              </td>
              <td>
                {doc.confidence_score != null
                  ? `${(doc.confidence_score * 100).toFixed(0)}%`
                  : '—'}
              </td>
              <td>{doc.uploaded_by}</td>
              <td>{doc.created_at ? new Date(doc.created_at).toLocaleDateString() : '—'}</td>
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
