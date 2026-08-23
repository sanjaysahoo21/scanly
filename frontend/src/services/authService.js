/**
 * Auth API Service
 * Handles all calls to the backend auth endpoints.
 * Stores the JWT token in localStorage after login/register.
 */

const BASE_URL = 'http://localhost:8080/api/v1'

/**
 * Register a new user + organization.
 * Returns { userId, email, role, token, expiresAt }
 */
export async function register({ email, password, fullName, organizationName }) {
  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, fullName, organizationName }),
  })

  const data = await res.json()

  if (!res.ok) {
    throw new Error(data.message || 'Registration failed')
  }

  // Save token to localStorage
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify({
    userId: data.userId,
    email: data.email,
    role: data.role,
    expiresAt: data.expiresAt,
  }))

  return data
}

/**
 * Login an existing user.
 * Returns { userId, email, role, token, expiresAt }
 */
export async function login({ email, password }) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })

  const data = await res.json()

  if (!res.ok) {
    throw new Error(data.message || 'Invalid email or password')
  }

  // Save token to localStorage
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify({
    userId: data.userId,
    email: data.email,
    role: data.role,
    expiresAt: data.expiresAt,
  }))

  return data
}

/**
 * Logout — clear stored token and user info.
 */
export function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
}

/**
 * Get the stored JWT token.
 */
export function getToken() {
  return localStorage.getItem('token')
}

/**
 * Get the stored user info.
 */
export function getUser() {
  const user = localStorage.getItem('user')
  return user ? JSON.parse(user) : null
}

/**
 * Check if the user is currently logged in.
 */
export function isLoggedIn() {
  return !!getToken()
}
