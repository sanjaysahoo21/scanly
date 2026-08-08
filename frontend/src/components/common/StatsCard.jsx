import './common.css'

function StatsCard({ icon: Icon, label, value, subtitle, className = '' }) {
  return (
    <div className={`stats-card card card-hoverable ${className}`}>
      <div className="stats-card-header">
        <div className="stats-card-icon-wrap">
          {Icon && <Icon size={20} strokeWidth={1.8} />}
        </div>
      </div>
      <div className="stats-card-body">
        <span className="stats-card-value">{value ?? '—'}</span>
        <span className="stats-card-label">{label}</span>
        {subtitle && <span className="stats-card-subtitle">{subtitle}</span>}
      </div>
    </div>
  )
}

export default StatsCard
