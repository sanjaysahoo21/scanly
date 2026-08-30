import { Outlet } from 'react-router-dom'
import Navbar from './Navbar.jsx'
import '../../styles/layout.css'

function AppLayout({ children }) {
  return (
    <div className="app-shell">
      <Navbar />
      <main className="app-main">
        {children || <Outlet />}
      </main>
    </div>
  )
}

export default AppLayout
