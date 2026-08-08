import { Receipt } from 'lucide-react'

function InvoicesPage() {
  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Invoices</h1>
        <p>Browse all extracted invoice data and manage audits.</p>
      </div>

      {/* Filter Bar */}
      <div className="filter-bar animate-fade-in-up stagger-1">
        <select className="input-field" style={{ width: 'auto', height: '36px', fontSize: 'var(--text-sm)' }}>
          <option value="">All Statuses</option>
          <option value="audited">Audited</option>
          <option value="unaudited">Not Audited</option>
        </select>
        <input
          type="text"
          className="input-field"
          placeholder="Search vendor name..."
          style={{ width: '240px', height: '36px', fontSize: 'var(--text-sm)' }}
        />
      </div>

      {/* Empty State */}
      <div className="table-container animate-fade-in-up stagger-2">
        <div className="empty-state">
          <Receipt className="empty-state-icon" />
          <h3>No invoices yet</h3>
          <p>Invoices will appear here once documents are processed by the AI engine.</p>
        </div>
      </div>
    </div>
  )
}

export default InvoicesPage
