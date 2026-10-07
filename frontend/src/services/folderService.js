/**
 * Folder API Service
 * Handles all calls to the backend /api/v1/folders endpoints.
 */

import { getToken } from './authService.js'

const BASE_URL = '/api/v1'

function authHeaders() {
  return { Authorization: `Bearer ${getToken()}`, 'Content-Type': 'application/json' }
}

/**
 * Fetch all folders for the current organization.
 * @returns {Promise<FolderResponse[]>}
 */
export async function getFolders() {
  const res = await fetch(`${BASE_URL}/folders`, {
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : []
  if (!res.ok) throw new Error(data.message || 'Failed to load folders')
  return data
}

/**
 * Create a new folder.
 * @param {{ name: string, description?: string, color?: string }} payload
 * @returns {Promise<FolderResponse>}
 */
export async function createFolder(payload) {
  const res = await fetch(`${BASE_URL}/folders`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(payload),
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || 'Failed to create folder')
  return data
}

/**
 * Update a folder's name / description / color.
 * @param {string} id
 * @param {{ name?: string, description?: string, color?: string }} payload
 * @returns {Promise<FolderResponse>}
 */
export async function updateFolder(id, payload) {
  const res = await fetch(`${BASE_URL}/folders/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(payload),
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || 'Failed to update folder')
  return data
}

/**
 * Delete a folder (its documents become unorganized).
 * @param {string} id
 */
export async function deleteFolder(id) {
  const res = await fetch(`${BASE_URL}/folders/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  if (!res.ok) {
    const text = await res.text()
    const data = text ? JSON.parse(text) : {}
    throw new Error(data.message || 'Failed to delete folder')
  }
}

/**
 * Move a document into a folder.
 * @param {string} folderId
 * @param {string} docId
 */
export async function addDocumentToFolder(folderId, docId) {
  const res = await fetch(`${BASE_URL}/folders/${folderId}/documents/${docId}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  if (!res.ok) {
    const text = await res.text()
    const data = text ? JSON.parse(text) : {}
    throw new Error(data.message || 'Failed to move document')
  }
}

/**
 * Remove a document from a folder (becomes unorganized).
 * @param {string} folderId
 * @param {string} docId
 */
export async function removeDocumentFromFolder(folderId, docId) {
  const res = await fetch(`${BASE_URL}/folders/${folderId}/documents/${docId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  if (!res.ok) {
    const text = await res.text()
    const data = text ? JSON.parse(text) : {}
    throw new Error(data.message || 'Failed to remove document from folder')
  }
}
