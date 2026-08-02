import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { username, role, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  useEffect(() => {
    setMenuOpen(false)
  }, [location.pathname])

  return (
    <>
      <header className="site-header">
        <Link to="/courses" className="brand">
          🌸 HanaTalk
        </Link>
        <button
          type="button"
          className="nav-toggle"
          aria-label="Menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          ☰
        </button>
        {menuOpen && <div className="nav-backdrop" onClick={() => setMenuOpen(false)} />}
        <nav className={menuOpen ? 'site-nav site-nav-open' : 'site-nav'}>
          <Link to="/flashcards">Flashcards</Link>
          <Link to="/chat">Chat practice</Link>
          <Link to="/achievements">Achievements</Link>
          {role === 'ADMIN' && <Link to="/admin/courses">Admin</Link>}
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
