import { ClipboardList } from 'lucide-react'
import '../../styles/audit.css'

function AuditLogTable({ logs = [] }) {
  if (logs.length === 0) {
    return (
      <div className="table-container">
        <div className="empty-state">
          <ClipboardList className="empty-state-icon" />
          <h3>No audit logs</h3>
          <p>Audit entries will appear here when documents are reviewed and edited.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="table-container animate-fade-in-up">
      <table>
        <thead>
          <tr>
            <th>User</th>
            <th>Entity</th>
            <th>Field</th>
            <th>Old Value</th>
            <th>New Value</th>
            <th>Action</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr key={log.id}>
              <td className="audit-user-cell">{log.user}</td>
              <td>
                <span className="audit-entity-badge">{log.entity_type}</span>
              </td>
              <td><code className="audit-field-name">{log.field_name}</code></td>
              <td className="audit-old-value">{log.old_value || '—'}</td>
              <td className="audit-new-value">{log.new_value || '—'}</td>
              <td>
                <span className={`audit-action audit-action-${log.action?.toLowerCase()}`}>
                  {log.action}
                </span>
              </td>
              <td className="audit-time">
                {log.created_at ? new Date(log.created_at).toLocaleString() : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default AuditLogTable
