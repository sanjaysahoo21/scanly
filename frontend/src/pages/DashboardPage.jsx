import {
  FileText,
  CheckCircle,
  Clock,
  AlertTriangle,
  TrendingUp,
  ArrowRight,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import StatsCard from '../components/common/StatsCard.jsx'
import DocumentTable from '../components/documents/DocumentTable.jsx'
import Button from '../components/common/Button.jsx'
import '../styles/dashboard.css'

function DashboardPage() {
  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Dashboard</h1>
        <p>Overview of your document processing pipeline.</p>
      </div>

      {/* Stats Grid */}
      <div className="stats-grid">
        <StatsCard
          icon={FileText}
          label="Total Documents"
          value="—"
          subtitle="All time"
          className="animate-fade-in-up stagger-1"
        />
        <StatsCard
          icon={Clock}
          label="Pending Review"
          value="—"
          subtitle="Awaiting audit"
          className="animate-fade-in-up stagger-2"
        />
        <StatsCard
          icon={CheckCircle}
          label="Completed Today"
          value="—"
          subtitle="Processed successfully"
          className="animate-fade-in-up stagger-3"
        />
        <StatsCard
          icon={AlertTriangle}
          label="Needs Review"
          value="—"
          subtitle="Low confidence"
          className="animate-fade-in-up stagger-4"
        />
      </div>

      {/* Processing Status Chart */}
      <div className="dashboard-chart-area animate-fade-in-up stagger-5">
        <h3>Processing Overview</h3>
        <div className="chart-placeholder">
          <TrendingUp size={40} strokeWidth={1.2} />
          <p>Charts will populate once documents are processed.</p>
        </div>
      </div>

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
        <DocumentTable documents={[]} />
      </div>
    </div>
  )
}

export default DashboardPage
