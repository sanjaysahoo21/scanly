import { getToken } from './authService.js'

const BASE = '/api/v1/integrations'

async function request(path = '', options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      ...(options.headers || {}),
    },
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || `Request failed (${res.status})`)
  return data
}

/** List all integrations for the logged-in org. */
export function getIntegrations() {
  return request()
}

/** Create a new integration. */
export function createIntegration(data) {
  return request('', { method: 'POST', body: JSON.stringify(data) })
}

/** Update an existing integration. */
export function updateIntegration(id, data) {
  return request(`/${id}`, { method: 'PUT', body: JSON.stringify(data) })
}

/** Delete an integration. */
export function deleteIntegration(id) {
  return request(`/${id}`, { method: 'DELETE' })
}

/**
 * Test a connection BEFORE saving.
 * Returns { success, message, latencyMs }
 */
export function testConnection(data) {
  return request('/test-connection', { method: 'POST', body: JSON.stringify(data) })
}

/**
 * Test an already-saved integration's connection.
 * Returns { success, message, latencyMs }
 */
export function testSavedConnection(id) {
  return request(`/${id}/test`, { method: 'POST' })
}

/**
 * Push invoices to target DB.
 * @param {string} id - Integration ID
 * @param {{ pushAll?: boolean, invoiceIds?: string[] }} opts
 */
export function pushInvoices(id, opts = { pushAll: true }) {
  return request(`/${id}/push`, { method: 'POST', body: JSON.stringify(opts) })
}

/** Get push history for an integration. */
export function getPushLogs(id) {
  return request(`/${id}/logs`)
}
