import { useState, useEffect } from 'react'
import {
  FileText,
  CheckCircle,
  Clock,
  AlertTriangle,
  TrendingUp,
  ArrowRight,
  XCircle,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import StatsCard from '../components/common/StatsCard.jsx'
import DocumentTable from '../components/documents/DocumentTable.jsx'
import Button from '../components/common/Button.jsx'
import StatusBadge from '../components/common/StatusBadge.jsx'
import { getDashboardStats } from '../services/dashboardService.js'
import '../styles/dashboard.css'

function DashboardPage() {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const data = await getDashboardStats()
        setStats(data)
      } catch (err) {
        setError(err.message)
      } finally {
        setLoading(false)
      }
    }
    fetchStats()
  }, [])

  const fmt = (n) => (loading ? '—' : String(n ?? 0))

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Dashboard</h1>
        <p>Overview of your document processing pipeline.</p>
      </div>

      {error && (
        <div className="upload-status upload-status-error animate-fade-in-up">
          {error}
        </div>
      )}

      {/* Stats Grid */}
      <div className="stats-grid">
        <StatsCard
          icon={FileText}
          label="Total Documents"
          value={fmt(stats?.totalDocuments)}
          subtitle="All time"
          className="animate-fade-in-up stagger-1"
        />
        <StatsCard
          icon={Clock}
          label="Pending"
          value={fmt(stats?.pending)}
          subtitle="Awaiting processing"
          className="animate-fade-in-up stagger-2"
        />
        <StatsCard
          icon={CheckCircle}
          label="Completed"
          value={fmt(stats?.completed)}
          subtitle="Processed successfully"
          className="animate-fade-in-up stagger-3"
        />
        <StatsCard
          icon={AlertTriangle}
          label="Needs Review"
          value={fmt(stats?.needsReview)}
          subtitle="Low confidence"
          className="animate-fade-in-up stagger-4"
        />
      </div>

      {/* Status Breakdown Bar */}
      {stats && stats.totalDocuments > 0 && (
        <div className="dashboard-chart-area animate-fade-in-up stagger-5">
          <h3>Status Breakdown</h3>
          <p className="chart-subtitle">Distribution of all documents across processing stages</p>
          
          <div className="progress-stacked-container">
            {/* The horizontal bar */}
            <div className="progress-stacked-bar">
              {stats.completed > 0 && <div className="progress-segment bg-success" style={{ width: `${(stats.completed / stats.totalDocuments) * 100}%` }} title="Completed" />}
              {stats.processing > 0 && <div className="progress-segment bg-accent" style={{ width: `${(stats.processing / stats.totalDocuments) * 100}%` }} title="Processing" />}
              {stats.pending > 0 && <div className="progress-segment bg-pending" style={{ width: `${(stats.pending / stats.totalDocuments) * 100}%` }} title="Pending" />}
              {stats.needsReview > 0 && <div className="progress-segment bg-warning" style={{ width: `${(stats.needsReview / stats.totalDocuments) * 100}%` }} title="Needs Review" />}
              {stats.failed > 0 && <div className="progress-segment bg-error" style={{ width: `${(stats.failed / stats.totalDocuments) * 100}%` }} title="Failed" />}
            </div>
            
            {/* The Legend */}
            <div className="progress-legend">
              {[
                { status: 'COMPLETED', label: 'Completed', value: stats.completed },
                { status: 'PROCESSING', label: 'Processing', value: stats.processing },
                { status: 'PENDING', label: 'Pending', value: stats.pending },
                { status: 'NEEDS_REVIEW', label: 'Needs Review', value: stats.needsReview },
                { status: 'FAILED', label: 'Failed', value: stats.failed },
              ].filter(item => item.value > 0).map(({ status, label, value }) => (
                <div key={status} className="legend-item">
                  <StatusBadge status={status} />
                  <span className="legend-value">{value}</span>
                  <span className="legend-percent">{((value / stats.totalDocuments) * 100).toFixed(0)}%</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {!stats && !loading && (
        <div className="dashboard-chart-area animate-fade-in-up stagger-5">
          <h3>Processing Overview</h3>
          <div className="chart-placeholder">
            <TrendingUp size={40} strokeWidth={1.2} />
            <p>Charts will populate once documents are processed.</p>
          </div>
        </div>
      )}

      {/* Recent Documents */}
      <div className="dashboard-section animate-fade-in-up stagger-6">
        <div className="dashboard-section-header">
          <h3>Recent Documents</h3>
          <Link to="/documents">
            <Button variant="ghost" size="sm">
              View All <ArrowRight size={14} />
            </Button>
          </Link>
        </div>
        {loading ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--color-text-muted)' }}>
            Loading...
          </div>
        ) : (
          <DocumentTable documents={stats?.recentDocuments ?? []} />
        )}
      </div>
    </div>
  )
}

export default DashboardPage
