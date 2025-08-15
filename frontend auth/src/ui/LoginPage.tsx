import React, { useState } from 'react'
import { useAuth } from '../utils/auth'

export const LoginPage: React.FC = () => {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await login({ username, password })
    } catch (err: any) {
      setError(err?.message ?? 'Login failed')
    }
  }

  return (
    <div className="panel">
      <h2>Login</h2>
      <form className="form" onSubmit={onSubmit}>
        <div className="col">
          <label htmlFor="username">Username</label>
          <input id="username" value={username} onChange={e => setUsername(e.target.value)} required autoComplete="username" />
        </div>
        <div className="col">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} required autoComplete="current-password" />
        </div>
        {error && <div className="error">{error}</div>}
        <div className="row">
          <button type="submit">Sign in</button>
          <button type="reset" className="ghost" onClick={() => { setUsername(''); setPassword(''); setError(null) }}>Reset</button>
        </div>
      </form>
    </div>
  )
}


