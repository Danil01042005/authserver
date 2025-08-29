import React, { useState } from 'react'
import { useAuth } from '../utils/auth'

export const HomePage: React.FC = () => {
  const { isAuthenticated, tokenPayload, refresh } = useAuth()
  const [msg, setMsg] = useState<string | null>(null)

  return (
    <div className="panel">
      <h2>Welcome</h2>
      {isAuthenticated ? (
        <>
          <p className="success">You are logged in.</p>
          <div className="row">
            <button onClick={async () => { setMsg(null); try { await refresh(); setMsg('Token refreshed') } catch (e:any) { setMsg(e?.message || 'Failed to refresh') } }}>Refresh token</button>
          </div>
          {msg && <div className="muted">{msg}</div>}
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


