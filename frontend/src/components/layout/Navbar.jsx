import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { Sun, Moon, Bell, User, Search, Command } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext.jsx'
import { logout } from '../../services/authService.js'
import '../../styles/layout.css'

const navItems = [
  { path: '/', label: 'Overview', exact: true },
  { path: '/documents', label: 'Documents' },
  { path: '/documents/upload', label: 'Upload' },
  { path: '/invoices', label: 'Invoices' },
  { path: '/audit-logs', label: 'Audit Logs' },
]

function Navbar() {
  const { theme, toggleTheme } = useTheme()
  const location = useLocation()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <nav className="topnav">
      <div className="topnav-inner">
        {/* Left: Logo + Nav Links */}
        <div className="topnav-left">
          <NavLink to="/" className="topnav-logo">
            <img src="/logo-s.png" alt="S" className="topnav-logo-img" />
            <span className="topnav-logo-text">canly</span>
          </NavLink>

          <div className="topnav-separator" />

          <div className="topnav-links">
            {navItems.map((item) => {
              const isActive = item.exact
                ? location.pathname === item.path
                : location.pathname.startsWith(item.path) && item.path !== '/'

              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={`topnav-link ${isActive ? 'topnav-link-active' : ''}`}
                >
                  {item.label}
                </NavLink>
              )
            })}
          </div>
        </div>

        {/* Right: Search + Actions */}
        <div className="topnav-right">
          <button className="topnav-search-trigger" id="search-trigger">
            <Search size={14} strokeWidth={2} />
            <span>Search...</span>
            <kbd className="topnav-kbd">
              <Command size={10} />K
            </kbd>
          </button>

          <div className="topnav-actions">
            <button
              className="topnav-icon-btn"
              onClick={toggleTheme}
              aria-label="Toggle theme"
              id="theme-toggle"
            >
              {theme === 'dark' ? (
                <Sun size={16} strokeWidth={2} />
              ) : (
                <Moon size={16} strokeWidth={2} />
              )}
            </button>

            <button className="topnav-icon-btn" aria-label="Notifications" id="notifications-btn">
              <Bell size={16} strokeWidth={2} />
            </button>

            <button className="topnav-avatar-btn" id="user-menu-btn" onClick={handleLogout} title="Sign out" aria-label="Sign out">
              <div className="topnav-avatar">
                <User size={14} strokeWidth={2} />
              </div>
            </button>
          </div>
        </div>
      </div>
    </nav>
  )
}

export default Navbar
