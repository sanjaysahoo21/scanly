import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Input from '../components/common/Input.jsx'
import Button from '../components/common/Button.jsx'
import { register } from '../services/authService.js'
import '../styles/auth.css'

function RegisterPage() {
  const [form, setForm] = useState({
    full_name: '',
    email: '',
    password: '',
    organization_name: '',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleChange = (field) => (e) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await register({
        email: form.email,
        password: form.password,
        fullName: form.full_name,
        organizationName: form.organization_name,
      })
      navigate('/')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-decoration" />
      <div className="auth-decoration-2" />

      <div className="auth-container">
        <div className="auth-brand">
          <div className="auth-brand-icon">S</div>
          <span className="auth-brand-text">Scanly</span>
        </div>

        <div className="auth-card">
          <h2>Create your account</h2>
          <p>Get started with intelligent document parsing.</p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <Input
              label="Full Name"
              id="full-name"
              placeholder="John Doe"
              value={form.full_name}
              onChange={handleChange('full_name')}
              required
            />
            <Input
              label="Email"
              type="email"
              id="email"
              placeholder="you@company.com"
              value={form.email}
              onChange={handleChange('email')}
              required
            />
            <Input
              label="Password"
              type="password"
              id="password"
              placeholder="Minimum 8 characters"
              value={form.password}
              onChange={handleChange('password')}
              required
            />
            <Input
              label="Organization Name"
              id="organization-name"
              placeholder="ACME Corp"
              value={form.organization_name}
              onChange={handleChange('organization_name')}
              required
            />
            {error && <p className="auth-error">{error}</p>}
            <Button type="submit" variant="primary" size="lg" id="register-button" disabled={loading}>
              {loading ? 'Creating account...' : 'Create Account'}
            </Button>
          </form>

          <div className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </div>
        </div>
      </div>
    </div>
  )
}

export default RegisterPage
