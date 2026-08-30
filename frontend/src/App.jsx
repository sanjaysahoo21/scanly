import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import DocumentsPage from './pages/DocumentsPage.jsx'
import DocumentUploadPage from './pages/DocumentUploadPage.jsx'
import DocumentDetailPage from './pages/DocumentDetailPage.jsx'
import InvoicesPage from './pages/InvoicesPage.jsx'
import InvoiceDetailPage from './pages/InvoiceDetailPage.jsx'
import AuditLogsPage from './pages/AuditLogsPage.jsx'
import { isLoggedIn } from './services/authService.js'

function ProtectedLayout() {
  const location = useLocation()
  if (!isLoggedIn()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <AppLayout><Outlet /></AppLayout>
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes — no layout shell */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Protected routes — wrapped in AppLayout */}
        <Route element={<ProtectedLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/documents/upload" element={<DocumentUploadPage />} />
          <Route path="/documents/:id" element={<DocumentDetailPage />} />
          <Route path="/invoices" element={<InvoicesPage />} />
          <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
          <Route path="/audit-logs" element={<AuditLogsPage />} />
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
