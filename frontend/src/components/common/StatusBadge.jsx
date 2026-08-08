import './common.css'

const statusConfig = {
  PENDING: { label: 'Pending', className: 'badge-pending' },
  PROCESSING: { label: 'Processing', className: 'badge-processing' },
  COMPLETED: { label: 'Completed', className: 'badge-success' },
  FAILED: { label: 'Failed', className: 'badge-error' },
  NEEDS_REVIEW: { label: 'Needs Review', className: 'badge-warning' },
}

function StatusBadge({ status }) {
  const config = statusConfig[status] || statusConfig.PENDING

  return (
    <span className={`badge ${config.className}`}>
      {config.className === 'badge-processing' && (
        <span className="badge-dot badge-dot-pulse" />
      )}
      {config.label}
    </span>
  )
}

export default StatusBadge
