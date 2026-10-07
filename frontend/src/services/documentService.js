/** Document API service. */
import { getToken } from './authService.js'

const BASE_URL = '/api/v1'
const MAX_FILES_PER_BATCH = 10

async function readResponse(res, fallbackMessage) {
  const text = await res.text()
  let data = {}
  try {
    data = text ? JSON.parse(text) : {}
  } catch {
    data = { message: fallbackMessage }
  }
  if (!res.ok) throw new Error(data.message || `${fallbackMessage} (${res.status})`)
  return data
}

async function uploadInBatches(files, endpoint, folderId, errorPrefix) {
  const jobs = []
  for (let start = 0; start < files.length; start += MAX_FILES_PER_BATCH) {
    const formData = new FormData()
    files.slice(start, start + MAX_FILES_PER_BATCH).forEach((file) => formData.append('files', file))
    if (folderId) formData.append('folderId', folderId)

    const res = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${getToken()}` },
      body: formData,
    })
    const data = await readResponse(res, errorPrefix)
    jobs.push(...(data.jobs || []))
  }
  return { message: 'Documents accepted for processing', totalFiles: jobs.length, jobs }
}

/** Upload one or more files in safe multipart batches. */
export function uploadDocuments(files, folderId = null) {
  return uploadInBatches(files, '/documents/upload', folderId, 'Upload failed')
}

/** Upload all supported files selected from a browser folder picker. */
export function uploadFolder(files, folderId = null) {
  return uploadInBatches(files, '/documents/upload-folder', folderId, 'Folder upload failed')
}

/** Fetch documents, optionally restricted to a folder. */
export async function getDocuments(folderId = null) {
  const url = folderId ? `${BASE_URL}/documents?folderId=${folderId}` : `${BASE_URL}/documents`
  const res = await fetch(url, { headers: { Authorization: `Bearer ${getToken()}` } })
  return readResponse(res, 'Failed to load documents')
}

/** Permanently delete a document (removes file and DB record). */
export async function deleteDocument(docId) {
  const res = await fetch(`${BASE_URL}/documents/${docId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  if (!res.ok) {
    const text = await res.text()
    let data = {}
    try { data = text ? JSON.parse(text) : {} } catch { /* ignore */ }
    throw new Error(data.message || `Delete failed (${res.status})`)
  }
}
