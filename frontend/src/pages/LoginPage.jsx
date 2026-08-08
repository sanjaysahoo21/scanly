import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Input from '../components/common/Input.jsx'
import Button from '../components/common/Button.jsx'
import '../styles/auth.css'

function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()

  const handleSubmit = (e) => {
    e.preventDefault()
    // TODO: Wire to auth API
    navigate('/')
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
          <h2>Welcome back</h2>
          <p>Sign in to your account to continue.</p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <Input
              label="Email"
              type="email"
              id="email"
              placeholder="you@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <Input
              label="Password"
              type="password"
              id="password"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <Button type="submit" variant="primary" size="lg" id="login-button">
              Sign In
            </Button>
          </form>

          <div className="auth-footer">
            Don't have an account? <Link to="/register">Create one</Link>
          </div>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
