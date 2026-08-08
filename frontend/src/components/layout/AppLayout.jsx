import { Outlet } from 'react-router-dom'
import Navbar from './Navbar.jsx'
import '../../styles/layout.css'

function AppLayout() {
  return (
    <div className="app-shell">
      <Navbar />
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}

export default AppLayout
