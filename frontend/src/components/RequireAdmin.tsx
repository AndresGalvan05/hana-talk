import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function RequireAdmin() {
  const { role } = useAuth()

  if (role !== 'ADMIN') {
    return <Navigate to="/courses" replace />
  }
  return <Outlet />
}
