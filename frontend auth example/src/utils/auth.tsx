import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { jwtDecode } from 'jwt-decode'
import { api } from './http'

type LoginDto = { username: string; password: string }
type JwtPayload = Record<string, any>

type AuthContextType = {
	isAuthenticated: boolean
	login: (dto: LoginDto) => Promise<void>
	logout: () => Promise<void>
	refresh: () => Promise<void>
	tokenPayload: JwtPayload | null
}

const AuthContext = createContext<AuthContextType | null>(null)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
	const [jwt, setJwt] = useState<string | null>(() => localStorage.getItem('jwt'))

	useEffect(() => {
		if (jwt) localStorage.setItem('jwt', jwt)
		else localStorage.removeItem('jwt')
	}, [jwt])

	// refresh хранится в httpOnly cookie, не в localStorage

	const tokenPayload = useMemo(() => {
		if (!jwt) return null
		try { return jwtDecode<JwtPayload>(jwt) } catch { return null }
	}, [jwt])

	const login = async ({ username, password }: LoginDto) => {
		const res = await api.post('/auth/login', { username, password })
		if (!res.ok) throw new Error(await res.text())
		const data = await res.json() as { jwt: string }
		setJwt(data.jwt)
	}

	const refresh = async () => {
		const res = await api.post('/auth/refresh')
		if (!res.ok) throw new Error(await res.text())
		const data = await res.json() as { jwt: string }
		setJwt(data.jwt)
	}


	const logout = async () => {
		try {
			await api.post('/auth/logout')
		} catch {
			// ignore
		} finally {
			setJwt(null)
		}
	}

	useEffect(() => {
		const onJwt = (e: Event) => {
			const detail = (e as CustomEvent<string | null>).detail
			setJwt(detail ?? null)
		}
		const onLogout = () => setJwt(null)
		window.addEventListener('auth:jwt' as any, onJwt as any)
		window.addEventListener('auth:logout', onLogout as any)
		return () => {
			window.removeEventListener('auth:jwt' as any, onJwt as any)
			window.removeEventListener('auth:logout', onLogout as any)
		}
	}, [])

	const value: AuthContextType = {
		isAuthenticated: !!jwt,
		login,
		logout,
		refresh,
		tokenPayload,
	}

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => {
	const ctx = useContext(AuthContext)
	if (!ctx) throw new Error('useAuth must be used within AuthProvider')
	return ctx
}


