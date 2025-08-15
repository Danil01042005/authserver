import React, { useState } from 'react'
import { api } from '../utils/http'

export const SignupPage: React.FC = () => {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setMessage(null)
    try {
      const res = await api.post('/auth/signup', { username, password })
      if (res.ok) setMessage('User registered successfully')
      else setError(await res.text())
    } catch (err: any) {
      setError(err?.message ?? 'Signup failed')
    }
  }

  return (
    <div className="panel">
      <h2>Sign up</h2>
      <form className="form" onSubmit={onSubmit}>
        <div className="col">
          <label htmlFor="username">Username</label>
          <input id="username" value={username} onChange={e => setUsername(e.target.value)} required autoComplete="username" />
        </div>
        <div className="col">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} required autoComplete="new-password" />
        </div>
        {message && <div className="success">{message}</div>}
        {error && <div className="error">{error}</div>}
        <div className="row">
          <button type="submit">Create account</button>
          <button type="reset" className="ghost" onClick={() => { setUsername(''); setPassword(''); setError(null); setMessage(null) }}>Reset</button>
        </div>
      </form>
    </div>
  )
}


