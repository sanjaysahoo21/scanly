import { useState, useRef, useEffect } from 'react'
import { Download, ChevronDown, FileText, FileJson } from 'lucide-react'
import { getToken } from '../../services/authService.js'

/**
 * ExportDropdown
 *
 * Renders a styled dropdown button with "Export as CSV" and "Export as JSON" options.
 *
 * Props:
 *   endpoint    — the API path, e.g. "/api/v1/invoices/export"
 *   filename    — base filename without extension, e.g. "invoices"
 *   extraParams — optional URLSearchParams-compatible object for additional query params
 *   disabled    — disables the button
 */
function ExportDropdown({ endpoint, filename = 'export', extraParams = {}, disabled = false }) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(null) // 'csv' | 'json' | null
  const [error, setError] = useState('')
  const ref = useRef(null)

  // Close dropdown when clicking outside
  useEffect(() => {
    function handler(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  async function doExport(format) {
    setOpen(false)
    setLoading(format)
    setError('')
    try {
      const params = new URLSearchParams({ format, ...extraParams })
      const res = await fetch(`${endpoint}?${params}`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      })
      if (!res.ok) throw new Error(`Export failed (${res.status})`)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${filename}.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(null)
    }
  }

  return (
    <div className="export-dropdown-wrapper" ref={ref}>
      <button
        className="export-btn"
        onClick={() => setOpen((v) => !v)}
        disabled={disabled || loading !== null}
        aria-haspopup="listbox"
        aria-expanded={open}
        id="export-dropdown-btn"
      >
        <Download size={15} />
        {loading ? 'Exporting…' : 'Export'}
        <ChevronDown size={14} style={{ transition: 'transform 0.2s', transform: open ? 'rotate(180deg)' : 'rotate(0deg)' }} />
      </button>

      {open && (
        <div className="export-dropdown-menu" role="listbox">
          <button
            className="export-dropdown-item"
            onClick={() => doExport('csv')}
            id="export-csv-btn"
          >
            <FileText size={15} />
            <div>
              <span className="export-item-label">Export as CSV</span>
              <span className="export-item-hint">Excel / Spreadsheet compatible</span>
            </div>
          </button>
          <button
            className="export-dropdown-item"
            onClick={() => doExport('json')}
            id="export-json-btn"
          >
            <FileJson size={15} />
            <div>
              <span className="export-item-label">Export as JSON</span>
              <span className="export-item-hint">Raw structured data</span>
            </div>
          </button>
        </div>
      )}

      {error && (
        <div className="export-error">{error}</div>
      )}
    </div>
  )
}

export default ExportDropdown
