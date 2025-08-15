import React from 'react'
import { Outlet } from 'react-router-dom'
import { AuthProvider } from './utils/auth'

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}


