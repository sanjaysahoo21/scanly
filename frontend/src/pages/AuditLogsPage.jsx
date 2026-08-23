import { useState, useEffect, useCallback } from 'react'
import AuditLogTable from '../components/audit/AuditLogTable.jsx'
import { getToken } from '../services/authService.js'

async function getAuditLogs({ action = '', page = 0 } = {}) {
  const token = getToken()
  const params = new URLSearchParams({ page, size: 20 })
  if (action) params.set('action', action)

  const res = await fetch(`/api/v1/audit-logs?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error('Failed to load audit logs')
  return data
}

function AuditLogsPage() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionFilter, setActionFilter] = useState('')
  const [totalElements, setTotalElements] = useState(0)
  const [currentPage, setCurrentPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const fetchLogs = useCallback(async (action, page) => {
    setLoading(true)
    setError('')
    try {
      const data = await getAuditLogs({ action, page })
      setLogs(data.logs ?? [])
      setTotalElements(data.totalElements ?? 0)
      setTotalPages(data.totalPages ?? 0)
      setCurrentPage(data.currentPage ?? 0)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLogs(actionFilter, 0)
  }, [actionFilter, fetchLogs])

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Audit Logs</h1>
        <p>Complete history of all data modifications and approvals.</p>
      </div>

      {/* Filter Bar */}
      <div className="filter-bar animate-fade-in-up stagger-1">
        <select
          className="input-field"
          style={{ width: 'auto', height: '36px', fontSize: 'var(--text-sm)' }}
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
        >
          <option value="">All Actions</option>
          <option value="EDIT">Edit</option>
          <option value="APPROVE">Approve</option>
          <option value="REJECT">Reject</option>
          <option value="REPROCESS">Reprocess</option>
        </select>
        {!loading && (
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>
            {totalElements} {totalElements === 1 ? 'entry' : 'entries'}
          </span>
        )}
      </div>

      {error && (
        <div className="upload-status upload-status-error animate-fade-in-up">
          {error}
        </div>
      )}

      {/* Audit Log Table */}
      <div className="animate-fade-in-up stagger-2">
        {loading ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--color-text-muted)' }}>
            Loading audit logs...
          </div>
        ) : (
          <AuditLogTable logs={logs} />
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginTop: '1.5rem' }}>
          <button
            className="topnav-icon-btn"
            disabled={currentPage === 0}
            onClick={() => fetchLogs(actionFilter, currentPage - 1)}
          >
            ←
          </button>
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', padding: '0.25rem 0.75rem' }}>
            Page {currentPage + 1} of {totalPages}
          </span>
          <button
            className="topnav-icon-btn"
            disabled={currentPage >= totalPages - 1}
            onClick={() => fetchLogs(actionFilter, currentPage + 1)}
          >
            →
          </button>
        </div>
      )}
    </div>
  )
}

export default AuditLogsPage
