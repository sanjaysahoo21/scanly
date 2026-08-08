import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  FileText,
  Upload,
  Receipt,
  ClipboardList,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import { useState } from 'react'
import '../../styles/layout.css'

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/documents', label: 'Documents', icon: FileText },
  { path: '/documents/upload', label: 'Upload', icon: Upload },
  { path: '/invoices', label: 'Invoices', icon: Receipt },
  { path: '/audit-logs', label: 'Audit Logs', icon: ClipboardList },
]

function Sidebar({ collapsed, onToggle }) {
  const location = useLocation()

  return (
    <aside className={`sidebar ${collapsed ? 'sidebar-collapsed' : ''}`}>
      <div className="sidebar-header">
        {!collapsed && (
          <div className="sidebar-brand animate-fade-in">
            <div className="sidebar-brand-icon">S</div>
            <span className="sidebar-brand-text">Scanly</span>
          </div>
        )}
        {collapsed && (
          <div className="sidebar-brand-icon sidebar-brand-icon-center">S</div>
        )}
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon
          const isActive =
            item.path === '/'
              ? location.pathname === '/'
              : location.pathname.startsWith(item.path)

          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={`sidebar-link ${isActive ? 'sidebar-link-active' : ''}`}
              title={collapsed ? item.label : undefined}
            >
              <Icon size={20} strokeWidth={1.8} />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          )
        })}
      </nav>

      <button
        className="sidebar-toggle"
        onClick={onToggle}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
      </button>
    </aside>
  )
}

export default Sidebar
