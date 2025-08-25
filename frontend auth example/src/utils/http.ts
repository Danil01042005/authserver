const API_BASE = (import.meta as any).env?.VITE_API_BASE || 'http://localhost:8080'

export const api = {
	get: (path: string, options: RequestInit = {}) => request('GET', path, options),
	post: (path: string, body?: any, options: RequestInit = {}) => request('POST', path, { ...options, body: body ? JSON.stringify(body) : undefined }),
}

async function request(method: string, path: string, options: RequestInit = {}) {
	const token = localStorage.getItem('jwt')
	const headers = new Headers(options.headers)
	if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
	if (token) headers.set('Authorization', `Bearer ${token}`)

	const res = await fetch(`${API_BASE}${path}`, { ...options, method, headers })
	return res
}


