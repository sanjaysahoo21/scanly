import { Receipt, Eye, Search, SlidersHorizontal, X, Download, FileText, FileJson, ChevronDown } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
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
    headers: { Authorization: `Bearer ${getToken()}`, 'Content-Type': 'application/json' },
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
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])
  async function doExport(format) {
    setOpen(false); setLoading(format); setError('')
    try { await exportBatch([...selectedIds], format) }
    catch (err) { setError(err.message) }
    finally { setLoading(null) }
  }
  return (
    <div className="export-dropdown-wrapper" ref={ref}>
      <button className="export-btn" onClick={() => setOpen(v => !v)} disabled={loading !== null} id="export-selected-btn">
        <Download size={15} />
        {loading ? 'Exporting…' : `Export ${selectedIds.size} selected`}
        <ChevronDown size={14} style={{ transition: 'transform 0.2s', transform: open ? 'rotate(180deg)' : 'rotate(0)' }} />
      </button>
      {open && (
        <div className="export-dropdown-menu" role="listbox">
          <button className="export-dropdown-item" onClick={() => doExport('csv')} id="export-sel-csv"><FileText size={15} /><div><span className="export-item-label">Export as CSV</span><span className="export-item-hint">Excel / Spreadsheet compatible</span></div></button>
          <button className="export-dropdown-item" onClick={() => doExport('json')} id="export-sel-json"><FileJson size={15} /><div><span className="export-item-label">Export as JSON</span><span className="export-item-hint">Raw structured data</span></div></button>
          <hr style={{ margin: '4px 8px', border: 'none', borderTop: '1px solid var(--color-border-light)' }} />
          <button className="export-dropdown-item" onClick={onClear} style={{ color: 'var(--color-text-muted)' }}>Clear selection</button>
        </div>
      )}
      {error && <div className="export-error">{error}</div>}
    </div>
  )
}

// ── Default filter state ─────────────────────────────────────────────────────
const DEFAULT_FILTERS = {
  q: '',
  audited: '',
  dateFrom: '',
  dateTo: '',
  amountMin: '',
  amountMax: '',
  sort: 'invoiceDate,desc',
}

function filtersActive(f) {
  return f.q || f.audited || f.dateFrom || f.dateTo || f.amountMin || f.amountMax
}

// ── Main Page ────────────────────────────────────────────────────────────────
function InvoicesPage() {
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(new Set())
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [showFilters, setShowFilters] = useState(false)
  const [totalElements, setTotalElements] = useState(0)

  // Debounce ref for text search
  const debounceRef = useRef(null)

  const fetchInvoices = useCallback((activeFilters) => {
    setLoading(true)
    const params = {
      q: activeFilters.q || undefined,
      audited: activeFilters.audited === 'true' ? true : activeFilters.audited === 'false' ? false : undefined,
      dateFrom: activeFilters.dateFrom || undefined,
      dateTo: activeFilters.dateTo || undefined,
      amountMin: activeFilters.amountMin ? Number(activeFilters.amountMin) : undefined,
      amountMax: activeFilters.amountMax ? Number(activeFilters.amountMax) : undefined,
      sort: activeFilters.sort,
    }
    getInvoices(params)
      .then((data) => { setInvoices(data.invoices || []); setTotalElements(data.totalElements || 0) })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  // Initial load
  useEffect(() => { fetchInvoices(DEFAULT_FILTERS) }, [fetchInvoices])

  // When non-text filters change, fetch immediately
  function applyFilter(key, value) {
    const next = { ...filters, [key]: value }
    setFilters(next)
    if (key !== 'q') {
      fetchInvoices(next)
    }
  }

  // Text search — debounced 400ms
  function handleSearch(value) {
    const next = { ...filters, q: value }
    setFilters(next)
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => fetchInvoices(next), 400)
  }

  function resetFilters() {
    setFilters(DEFAULT_FILTERS)
    fetchInvoices(DEFAULT_FILTERS)
  }

  const allChecked = invoices.length > 0 && selected.size === invoices.length
  function toggleAll() { setSelected(allChecked ? new Set() : new Set(invoices.map(inv => inv.id))) }
  function toggleOne(id) {
    setSelected(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })
  }

  const hasActiveFilters = filtersActive(filters)

  return (
    <div className="page-content">
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="page-header animate-fade-in-up" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>Invoices</h1>
          <p>
            {loading ? 'Loading…' : `${totalElements} invoice${totalElements !== 1 ? 's' : ''}${hasActiveFilters ? ' matched' : ''}`}
          </p>
        </div>
        {selected.size > 0
          ? <SelectionExportDropdown selectedIds={selected} onClear={() => setSelected(new Set())} />
          : <ExportDropdown endpoint="/api/v1/invoices/export" filename="invoices" disabled={loading || invoices.length === 0} />
        }
      </div>

      {/* ── Search + Filter Bar ─────────────────────────────────────────── */}
      <div className="invoice-search-bar animate-fade-in-up stagger-1">
        {/* Text search */}
        <div className="invoice-search-input-wrapper">
          <Search size={15} className="invoice-search-icon" />
          <input
            id="invoice-search-input"
            className="invoice-search-input"
            type="text"
            placeholder="Search by vendor, buyer or invoice number…"
            value={filters.q}
            onChange={(e) => handleSearch(e.target.value)}
          />
          {filters.q && (
            <button className="invoice-search-clear" onClick={() => handleSearch('')} aria-label="Clear search">
              <X size={13} />
            </button>
          )}
        </div>

        {/* Status quick filter */}
        <select
          className="input-field invoice-filter-select"
          value={filters.audited}
          onChange={(e) => applyFilter('audited', e.target.value)}
          id="invoice-status-filter"
        >
          <option value="">All Statuses</option>
          <option value="true">Audited</option>
          <option value="false">Needs Review</option>
        </select>

        {/* Sort */}
        <select
          className="input-field invoice-filter-select"
          value={filters.sort}
          onChange={(e) => applyFilter('sort', e.target.value)}
          id="invoice-sort-select"
        >
          <option value="invoiceDate,desc">Date ↓ newest</option>
          <option value="invoiceDate,asc">Date ↑ oldest</option>
          <option value="totalAmount,desc">Amount ↓ highest</option>
          <option value="totalAmount,asc">Amount ↑ lowest</option>
          <option value="vendorName,asc">Vendor A→Z</option>
          <option value="vendorName,desc">Vendor Z→A</option>
        </select>

        {/* More filters toggle */}
        <button
          className={`invoice-filter-toggle-btn ${showFilters ? 'active' : ''} ${hasActiveFilters && !showFilters ? 'has-active' : ''}`}
          onClick={() => setShowFilters(v => !v)}
          id="invoice-filter-toggle"
        >
          <SlidersHorizontal size={14} />
          {hasActiveFilters && !showFilters ? 'Filters active' : 'Filters'}
        </button>

        {hasActiveFilters && (
          <button className="invoice-filter-reset-btn" onClick={resetFilters} id="invoice-filter-reset">
            <X size={13} /> Reset
          </button>
        )}
      </div>

      {/* ── Advanced Filter Panel ───────────────────────────────────────── */}
      {showFilters && (
        <div className="invoice-filter-panel animate-fade-in-up">
          <div className="invoice-filter-panel-grid">
            <div className="filter-field">
              <label className="filter-label">Date from</label>
              <input type="date" className="input-field" value={filters.dateFrom}
                onChange={(e) => applyFilter('dateFrom', e.target.value)} id="filter-date-from" />
            </div>
            <div className="filter-field">
              <label className="filter-label">Date to</label>
              <input type="date" className="input-field" value={filters.dateTo}
                onChange={(e) => applyFilter('dateTo', e.target.value)} id="filter-date-to" />
            </div>
            <div className="filter-field">
              <label className="filter-label">Min amount</label>
              <input type="number" className="input-field" placeholder="e.g. 100" value={filters.amountMin}
                onChange={(e) => applyFilter('amountMin', e.target.value)} id="filter-amount-min" />
            </div>
            <div className="filter-field">
              <label className="filter-label">Max amount</label>
              <input type="number" className="input-field" placeholder="e.g. 50000" value={filters.amountMax}
                onChange={(e) => applyFilter('amountMax', e.target.value)} id="filter-amount-max" />
            </div>
          </div>
        </div>
      )}

      {error && <div className="upload-status upload-status-error">{error}</div>}

      {/* ── Table ──────────────────────────────────────────────────────── */}
      {loading ? (
        <p style={{ color: 'var(--color-text-muted)', marginTop: 'var(--space-4)' }}>Loading invoices…</p>
      ) : invoices.length === 0 ? (
        <div className="table-container">
          <div className="empty-state">
            <Receipt className="empty-state-icon" />
            <h3>{hasActiveFilters ? 'No results found' : 'No invoices yet'}</h3>
            <p>{hasActiveFilters ? 'Try adjusting your filters.' : 'Invoices appear after document processing completes.'}</p>
            {hasActiveFilters && <button className="btn-secondary" onClick={resetFilters} style={{ marginTop: 'var(--space-3)' }}>Clear filters</button>}
          </div>
        </div>
      ) : (
        <div className="table-container animate-fade-in-up stagger-2">
          <table>
            <thead>
              <tr>
                <th style={{ width: '40px', paddingLeft: 'var(--space-4)' }}>
                  <input type="checkbox" className="row-checkbox" checked={allChecked}
                    onChange={toggleAll} aria-label="Select all" id="select-all-invoices" />
                </th>
                <th>Invoice</th>
                <th>Vendor</th>
                <th>Date</th>
                <th>Total</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr key={invoice.id} className={selected.has(invoice.id) ? 'row-selected' : ''} onClick={() => toggleOne(invoice.id)} style={{ cursor: 'pointer' }}>
                  <td style={{ paddingLeft: 'var(--space-4)' }} onClick={(e) => e.stopPropagation()}>
                    <input type="checkbox" className="row-checkbox" checked={selected.has(invoice.id)}
                      onChange={() => toggleOne(invoice.id)} id={`sel-${invoice.id}`} />
                  </td>
                  <td>{invoice.invoiceNumber || '—'}</td>
                  <td>{invoice.vendorName || '—'}</td>
                  <td>{invoice.invoiceDate || '—'}</td>
                  <td>{invoice.totalAmount ?? '—'} {invoice.currency || ''}</td>
                  <td><StatusBadge status={invoice.isAudited ? 'COMPLETED' : 'NEEDS_REVIEW'} /></td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <Link to={`/invoices/${invoice.id}`} aria-label="View invoice"><Eye size={16} /></Link>
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
