/**
 * Document API Service
 * Handles all calls to the backend document endpoints.
 */

import { getToken } from './authService.js'

const BASE_URL = '/api/v1'

/**
 * Upload one or more files for processing.
 * @param {File[]} files - Array of File objects
 * @returns {Promise<{ message, totalFiles, jobs }>}
 */
export async function uploadDocuments(files) {
  const token = getToken()

  const formData = new FormData()
  files.forEach((file) => {
    formData.append('files', file)
  })

  const res = await fetch(`${BASE_URL}/documents/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      // Note: Do NOT set Content-Type here — browser sets it automatically with boundary
    },
    body: formData,
  })

  // Safely parse response — server may return empty body on 401/403
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}

  if (!res.ok) {
    throw new Error(data.message || `Upload failed (${res.status})`)
  }

  return data
}

/**
 * Fetch all documents for the current user's organization.
 */
export async function getDocuments() {
  const token = getToken()

  const res = await fetch(`${BASE_URL}/documents`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : []

  if (!res.ok) {
    throw new Error('Failed to load documents')
  }

  return data
}
