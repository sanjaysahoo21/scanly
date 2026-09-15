import { Receipt, Eye, Download, FileText, FileJson, ChevronDown } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getInvoices } from '../services/invoiceService.js'
import { getToken } from '../services/authService.js'
import StatusBadge from '../components/common/StatusBadge.jsx'
import ExportDropdown from '../components/common/ExportDropdown.jsx'
import '../styles/invoices.css'

// ── Batch export helper ──────────────────────────────────────────────────────
async function exportBatch(ids, format) {
  const res = await fetch(`/api/v1/invoices/export/batch?format=${format}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${getToken()}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ ids }),
  })
  if (!res.ok) throw new Error(`Export failed (${res.status})`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `invoices-selected.${format}`
  a.click()
  URL.revokeObjectURL(url)
}

// ── Selection Export Dropdown ────────────────────────────────────────────────
function SelectionExportDropdown({ selectedIds, onClear }) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(null)
  const [error, setError] = useState('')
  const ref = useRef(null)

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  async function doExport(format) {
    setOpen(false)
    setLoading(format)
    setError('')
    try {
      await exportBatch([...selectedIds], format)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(null)
    }
  }

  return (
    <div className="export-dropdown-wrapper" ref={ref}>
      <button
        className="export-btn selection-export-btn"
        onClick={() => setOpen((v) => !v)}
        disabled={loading !== null}
        id="export-selected-btn"
      >
        <Download size={15} />
        {loading ? 'Exporting…' : `Export ${selectedIds.size} selected`}
        <ChevronDown size={14} style={{ transition: 'transform 0.2s', transform: open ? 'rotate(180deg)' : 'rotate(0deg)' }} />
      </button>
      {open && (
        <div className="export-dropdown-menu" role="listbox">
          <button className="export-dropdown-item" onClick={() => doExport('csv')} id="export-selected-csv-btn">
            <FileText size={15} />
            <div>
              <span className="export-item-label">Export as CSV</span>
              <span className="export-item-hint">Excel / Spreadsheet compatible</span>
            </div>
          </button>
          <button className="export-dropdown-item" onClick={() => doExport('json')} id="export-selected-json-btn">
            <FileJson size={15} />
            <div>
              <span className="export-item-label">Export as JSON</span>
              <span className="export-item-hint">Raw structured data</span>
            </div>
          </button>
          <hr style={{ margin: '4px 8px', border: 'none', borderTop: '1px solid var(--color-border-light)' }} />
          <button className="export-dropdown-item" onClick={onClear} style={{ color: 'var(--color-text-muted)' }}>
            Clear selection
          </button>
        </div>
      )}
      {error && <div className="export-error">{error}</div>}
    </div>
  )
}

// ── Main Page ────────────────────────────────────────────────────────────────
function InvoicesPage() {
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(new Set()) // Set of invoice IDs

  useEffect(() => {
    getInvoices()
      .then((data) => setInvoices(data.invoices || []))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  const allChecked = invoices.length > 0 && selected.size === invoices.length

  function toggleAll() {
    if (allChecked) {
      setSelected(new Set())
    } else {
      setSelected(new Set(invoices.map((inv) => inv.id)))
    }
  }

  function toggleOne(id) {
    setSelected((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  return (
    <div className="page-content">
      {/* Header */}
      <div
        className="page-header animate-fade-in-up"
        style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}
      >
        <div>
          <h1>Invoices</h1>
          <p>Browse extracted invoice data and manage audits.</p>
        </div>

        {/* Show selection toolbar when rows are checked, otherwise show global export */}
        {selected.size > 0 ? (
          <SelectionExportDropdown selectedIds={selected} onClear={() => setSelected(new Set())} />
        ) : (
          <ExportDropdown
            endpoint="/api/v1/invoices/export"
            filename="invoices"
            disabled={loading || invoices.length === 0}
          />
        )}
      </div>

      {error && <div className="upload-status upload-status-error">{error}</div>}

      {loading ? (
        <p>Loading invoices...</p>
      ) : invoices.length === 0 ? (
        <div className="table-container">
          <div className="empty-state">
            <Receipt className="empty-state-icon" />
            <h3>No invoices yet</h3>
            <p>Invoices appear after document processing completes.</p>
          </div>
        </div>
      ) : (
        <div className="table-container animate-fade-in-up stagger-1">
          <table>
            <thead>
              <tr>
                {/* Select-all checkbox */}
                <th style={{ width: '40px', paddingLeft: 'var(--space-4)' }}>
                  <input
                    type="checkbox"
                    className="row-checkbox"
                    checked={allChecked}
                    onChange={toggleAll}
                    aria-label="Select all invoices"
                    id="select-all-invoices"
                  />
                </th>
                <th>Invoice</th>
                <th>Vendor</th>
                <th>Total</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr
                  key={invoice.id}
                  className={selected.has(invoice.id) ? 'row-selected' : ''}
                  onClick={() => toggleOne(invoice.id)}
                  style={{ cursor: 'pointer' }}
                >
                  <td style={{ paddingLeft: 'var(--space-4)' }} onClick={(e) => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      className="row-checkbox"
                      checked={selected.has(invoice.id)}
                      onChange={() => toggleOne(invoice.id)}
                      aria-label={`Select invoice ${invoice.invoiceNumber || invoice.id}`}
                      id={`select-invoice-${invoice.id}`}
                    />
                  </td>
                  <td>{invoice.invoiceNumber || '—'}</td>
                  <td>{invoice.vendorName || '—'}</td>
                  <td>{invoice.totalAmount ?? '—'} {invoice.currency || ''}</td>
                  <td><StatusBadge status={invoice.isAudited ? 'COMPLETED' : 'NEEDS_REVIEW'} /></td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <Link to={`/invoices/${invoice.id}`} aria-label="View invoice">
                      <Eye size={16} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {selected.size > 0 && (
            <div className="selection-count-bar">
              {selected.size} of {invoices.length} selected
              <button className="selection-clear-btn" onClick={() => setSelected(new Set())}>Clear</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default InvoicesPage
