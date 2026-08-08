import { Loader2 } from 'lucide-react'
import './common.css'

function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  icon: Icon,
  onClick,
  type = 'button',
  className = '',
  id,
}) {
  return (
    <button
      type={type}
      className={`btn btn-${variant} btn-${size} ${loading ? 'btn-loading' : ''} ${className}`}
      disabled={disabled || loading}
      onClick={onClick}
      id={id}
    >
      {loading ? (
        <Loader2 size={16} className="btn-spinner" />
      ) : Icon ? (
        <Icon size={16} strokeWidth={1.8} />
      ) : null}
      <span>{children}</span>
    </button>
  )
}

export default Button
