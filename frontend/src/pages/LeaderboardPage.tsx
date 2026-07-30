import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/client'
import type { LeaderboardEntry } from '../api/types'

export function LeaderboardPage() {
  const { username } = useAuth()
  const [entries, setEntries] = useState<LeaderboardEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<LeaderboardEntry[]>('/api/leaderboard')
      .then(setEntries)
      .catch(() => setError('Could not load the leaderboard.'))
  }, [])

  return (
    <>
      <h1>Leaderboard</h1>
      {error && <p className="error">{error}</p>}
      {entries !== null && entries.length === 0 && (
        <p className="muted">No streaks yet — be the first to start one!</p>
      )}
      {entries !== null && entries.length > 0 && (
        <ol className="leaderboard-list">
          {entries.map((entry, i) => (
            <li
              key={entry.userId}
              className={entry.username === username ? 'card leaderboard-row leaderboard-row-me' : 'card leaderboard-row'}
            >
              <span className="leaderboard-rank">{i + 1}</span>
              <span className="leaderboard-username">{entry.username}</span>
              <span className="leaderboard-streak">🔥 {entry.currentStreak}</span>
            </li>
          ))}
        </ol>
      )}
    </>
  )
}
