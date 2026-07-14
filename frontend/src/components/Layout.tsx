import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { username, logout } = useAuth()

  return (
    <>
      <header className="site-header">
        <Link to="/courses" className="brand">
          🌸 HanaTalk
        </Link>
        <nav>
          <span className="nav-user">{username}</span>
          <button type="button" className="link-button" onClick={logout}>
            Log out
          </button>
        </nav>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
