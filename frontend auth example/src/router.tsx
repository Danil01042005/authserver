import React from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './ui/AppLayout'
import { Providers } from './ui/_providers'
import { LoginPage } from './ui/LoginPage'
import { SignupPage } from './ui/SignupPage'
import { HomePage } from './ui/HomePage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: (
      <Providers>
        <AppLayout />
      </Providers>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
    ],
  },
])


