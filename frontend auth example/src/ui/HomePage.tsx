import React from 'react'
import { useAuth } from '../utils/auth'

export const HomePage: React.FC = () => {
  const { isAuthenticated, tokenPayload } = useAuth()

  return (
    <div className="panel">
      <h2>Welcome</h2>
      {isAuthenticated ? (
        <>
          <p className="success">You are logged in.</p>
          {tokenPayload && (
            <pre className="panel" style={{ overflow: 'auto' }}>{JSON.stringify(tokenPayload, null, 2)}</pre>
          )}
        </>
      ) : (
        <p className="muted">Please log in or sign up to continue.</p>
      )}
    </div>
  )
}


