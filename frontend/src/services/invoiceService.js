import { getToken } from './authService.js'

const BASE_URL = '/api/v1/invoices'

async function request(path = '') {
  const res = await fetch(`${BASE_URL}${path}`, { headers: { Authorization: `Bearer ${getToken()}` } })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || 'Failed to load invoices')
  return data
}

/**
 * Fetch invoices with optional search/filter params.
 *
 * @param {object} filters
 *   - q         {string}  text search
 *   - audited   {boolean|null}  null = all
 *   - dateFrom  {string}  YYYY-MM-DD
 *   - dateTo    {string}  YYYY-MM-DD
 *   - amountMin {number}
 *   - amountMax {number}
 *   - page      {number}
 *   - size      {number}
 *   - sort      {string}  e.g. "invoiceDate,desc"
 */
export function getInvoices(filters = {}) {
  const params = new URLSearchParams()
  const { q, audited, dateFrom, dateTo, amountMin, amountMax,
          page = 0, size = 20, sort = 'invoiceDate,desc' } = filters

  if (q && q.trim())        params.set('q', q.trim())
  if (audited != null)      params.set('audited', String(audited))
  if (dateFrom)             params.set('dateFrom', dateFrom)
  if (dateTo)               params.set('dateTo', dateTo)
  if (amountMin != null)    params.set('amountMin', String(amountMin))
  if (amountMax != null)    params.set('amountMax', String(amountMax))
  params.set('page', String(page))
  params.set('size', String(size))
  params.set('sort', sort)

  return request(`?${params.toString()}`)
}

export function getInvoice(id) {
  return request(`/${id}`)
}

