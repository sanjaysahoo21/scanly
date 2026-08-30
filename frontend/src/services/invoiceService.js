import { getToken } from './authService.js'

const BASE_URL = '/api/v1/invoices'

async function request(path = '') {
  const res = await fetch(`${BASE_URL}${path}`, { headers: { Authorization: `Bearer ${getToken()}` } })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || 'Failed to load invoices')
  return data
}

export function getInvoices(page = 0) {
  return request(`?page=${page}&size=20`)
}

export function getInvoice(id) {
  return request(`/${id}`)
}
