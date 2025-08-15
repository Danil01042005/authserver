import React from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../utils/auth'

export const AppLayout: React.FC = () => {
  const { isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="container">
      <header className="header">
        <div className="brand">
          <div className="brand-badge">🔒</div>
          Secure LMS
        </div>
        <nav className="row" role="navigation">
          <Link to="/">Home</Link>
          {isAuthenticated ? (
            <button className="ghost" onClick={() => { logout(); navigate('/'); }}>Logout</button>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/signup">Sign up</Link>
            </>
          )}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}


