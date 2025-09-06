const API_BASE = (import.meta as any).env?.VITE_API_BASE || 'http://localhost:8080'

export const api = {
	get: (path: string, options: RequestInit = {}) => request('GET', path, options),
	post: (path: string, body?: any, options: RequestInit = {}) => request('POST', path, { ...options, body: body !== undefined ? JSON.stringify(body) : undefined }),
}

let refreshPromise: Promise<string> | null = null

function getJwt(): string | null {
	return localStorage.getItem('jwt')
}

function setJwt(jwt: string | null) {
	if (jwt) localStorage.setItem('jwt', jwt)
	else localStorage.removeItem('jwt')
	window.dispatchEvent(new CustomEvent('auth:jwt', { detail: jwt }))
}

function isAuthPath(path: string): boolean {
	return path.startsWith('/auth/')
}

async function performRefresh(): Promise<string> {
	if (!refreshPromise) {
		refreshPromise = (async () => {
			const res = await fetch(`${API_BASE}/auth/refresh`, {
				method: 'POST',
				credentials: 'include',
				headers: { 'Content-Type': 'application/json' },
			})
			if (!res.ok) {
				throw new Error('refresh failed')
			}
			const data = await res.json() as { jwt: string }
			setJwt(data.jwt)
			return data.jwt
		})()
	}
	try {
		return await refreshPromise
	} finally {
		refreshPromise = null
	}
}

async function request(method: string, path: string, options: RequestInit = {}) {
	const attempt = async (): Promise<Response> => {
		const token = getJwt()
		const headers = new Headers(options.headers)
		if (options.body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
		if (token) headers.set('Authorization', `Bearer ${token}`)
		return fetch(`${API_BASE}${path}`, { ...options, method, headers, credentials: 'include' })
	}


	let res = await attempt()
	if (res.status !== 401 || isAuthPath(path)) {
		return res
	}

	try {
		await performRefresh()
		res = await attempt()
		if (res.status === 401) throw new Error('unauthorized after refresh')
		return res
	} catch {
		setJwt(null)
		window.dispatchEvent(new CustomEvent('auth:logout'))
		// Редирект на логин при неуспешной повторной попытке/refresh
		try { if (!isAuthPath(path)) window.location.href = '/login' } catch {}
		return res
	}
}


