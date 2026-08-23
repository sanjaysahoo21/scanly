/**
 * Dashboard API Service
 */
import { getToken } from './authService.js'

const BASE_URL = '/api/v1'

/**
 * Fetch dashboard statistics for the current user's organization.
 * @returns {Promise<{ totalDocuments, pending, processing, completed, failed, needsReview, recentDocuments }>}
 */
export async function getDashboardStats() {
  const token = getToken()

  const res = await fetch(`${BASE_URL}/dashboard/stats`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : {}

  if (!res.ok) {
    throw new Error('Failed to load dashboard stats')
  }

  return data
}
