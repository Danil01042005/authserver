import React, { useState } from 'react'
import { useAuth } from '../utils/auth'
import { api } from '../utils/http'

export const HomePage: React.FC = () => {
  const { isAuthenticated, tokenPayload, refresh } = useAuth()
  const [msg, setMsg] = useState<string | null>(null)
  const [me, setMe] = useState<any | null>(null)
  const [loading, setLoading] = useState(false)

  return (
    <div className="panel">
      <h2>Welcome</h2>
      {isAuthenticated ? (
        <>
          <p className="success">You are logged in.</p>
          <div className="row">
            <button onClick={async () => { setMsg(null); try { await refresh(); setMsg('Token refreshed') } catch (e:any) { setMsg(e?.message || 'Failed to refresh') } }}>Refresh token</button>
            <button onClick={async () => {
              setMsg(null)
              setMe(null)
              setLoading(true)
              try {
                const res = await api.get('/example-under-armor/me')
                if (!res.ok) throw new Error(await res.text())
                const data = await res.json()
                setMe(data)
              } catch (e:any) {
                setMsg(e?.message || 'Failed to fetch /me')
              } finally {
                setLoading(false)
              }
            }}>Me</button>
          </div>
          {msg && <div className="muted">{msg}</div>}
          {loading && <div className="muted">Loading...</div>}
          {me && (
            <pre className="panel" style={{ overflow: 'auto' }}>{JSON.stringify(me, null, 2)}</pre>
          )}
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


