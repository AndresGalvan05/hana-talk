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
          <Link to="/flashcards">Flashcards</Link>
          <Link to="/chat">Chat practice</Link>
          <Link to="/achievements">Achievements</Link>
          <Link to="/profile" className="nav-user">
            {username}
          </Link>
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
