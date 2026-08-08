import { Upload } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../components/common/Button.jsx'
import DocumentTable from '../components/documents/DocumentTable.jsx'

function DocumentsPage() {
  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1>Documents</h1>
            <p>View and manage all uploaded documents.</p>
          </div>
          <Link to="/documents/upload">
            <Button variant="primary" icon={Upload}>
              Upload Documents
            </Button>
          </Link>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="filter-bar animate-fade-in-up stagger-1">
        <select className="input-field" style={{ width: 'auto', height: '36px', fontSize: 'var(--text-sm)' }}>
          <option value="">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="PROCESSING">Processing</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
          <option value="NEEDS_REVIEW">Needs Review</option>
        </select>
      </div>

      {/* Document Table */}
      <div className="animate-fade-in-up stagger-2">
        <DocumentTable documents={[]} />
      </div>
    </div>
  )
}

export default DocumentsPage
