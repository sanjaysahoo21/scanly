import AuditLogTable from '../components/audit/AuditLogTable.jsx'

function AuditLogsPage() {
  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Audit Logs</h1>
        <p>Complete history of all data modifications and approvals.</p>
      </div>

      {/* Filter Bar */}
      <div className="filter-bar animate-fade-in-up stagger-1">
        <select className="input-field" style={{ width: 'auto', height: '36px', fontSize: 'var(--text-sm)' }}>
          <option value="">All Actions</option>
          <option value="EDIT">Edit</option>
          <option value="APPROVE">Approve</option>
          <option value="REJECT">Reject</option>
          <option value="REPROCESS">Reprocess</option>
        </select>
        <input
          type="date"
          className="input-field"
          style={{ width: 'auto', height: '36px', fontSize: 'var(--text-sm)' }}
        />
      </div>

      {/* Audit Log Table */}
      <div className="animate-fade-in-up stagger-2">
        <AuditLogTable logs={[]} />
      </div>
    </div>
  )
}

export default AuditLogsPage
