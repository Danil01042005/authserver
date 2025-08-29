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
	const [refreshToken, setRefreshToken] = useState<string | null>(() => localStorage.getItem('refresh'))

	useEffect(() => {
		if (jwt) localStorage.setItem('jwt', jwt)
		else localStorage.removeItem('jwt')
	}, [jwt])

	useEffect(() => {
		if (refreshToken) localStorage.setItem('refresh', refreshToken)
		else localStorage.removeItem('refresh')
	}, [refreshToken])

	const tokenPayload = useMemo(() => {
		if (!jwt) return null
		try { return jwtDecode<JwtPayload>(jwt) } catch { return null }
	}, [jwt])

	const login = async ({ username, password }: LoginDto) => {
		const res = await api.post('/auth/login', { username, password })
		if (!res.ok) throw new Error(await res.text())
		const data = await res.json() as { jwt: string; refreshToken?: string }
		setJwt(data.jwt)
		if (data.refreshToken) setRefreshToken(data.refreshToken)
	}

	const refresh = async () => {
		if (!refreshToken) throw new Error('No refresh token')
		const res = await api.post('/auth/refresh', { refreshToken })
		if (!res.ok) throw new Error(await res.text())
		const data = await res.json() as { jwt: string; refreshToken?: string }
		setJwt(data.jwt)
		if (data.refreshToken) setRefreshToken(data.refreshToken)
	}


	const logout = async () => {
		try {
			if (refreshToken) {
				await api.post('/auth/logout', { refreshToken })
			}
		} catch {
			// ignore
		} finally {
			setJwt(null); setRefreshToken(null)
		}
	}

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


