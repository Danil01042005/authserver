import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { jwtDecode } from 'jwt-decode'
import { api } from './http'

type LoginDto = { username: string; password: string }
type JwtPayload = Record<string, any>

type AuthContextType = {
	isAuthenticated: boolean
	login: (dto: LoginDto) => Promise<void>
	logout: () => void
	tokenPayload: JwtPayload | null
}

const AuthContext = createContext<AuthContextType | null>(null)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
	const [jwt, setJwt] = useState<string | null>(() => localStorage.getItem('jwt'))

	useEffect(() => {
		if (jwt) localStorage.setItem('jwt', jwt)
		else localStorage.removeItem('jwt')
	}, [jwt])

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

	const logout = () => setJwt(null)

	const value: AuthContextType = {
		isAuthenticated: !!jwt,
		login,
		logout,
		tokenPayload,
	}

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => {
	const ctx = useContext(AuthContext)
	if (!ctx) throw new Error('useAuth must be used within AuthProvider')
	return ctx
}


