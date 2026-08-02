import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Achievement } from '../api/types'

export function AchievementsPage() {
  const [achievements, setAchievements] = useState<Achievement[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<Achievement[]>('/api/users/me/achievements')
      .then(setAchievements)
      .catch(() => setError('Could not load your achievements.'))
  }, [])

  return (
    <>
      <h1>Achievements</h1>
      {error && <p className="error">{error}</p>}
      {achievements !== null && (
        <div className="achievement-grid">
          {achievements.map((a) => (
            <div key={a.code} className={a.unlocked ? 'card achievement-card' : 'card achievement-card achievement-locked'}>
              <p className="achievement-title">
                {a.unlocked ? '✅' : '🔒'} {a.title}
              </p>
              <p className="muted">{a.description}</p>
              {a.unlocked && a.unlockedAt && (
                <p className="muted achievement-unlocked-at">Unlocked {new Date(a.unlockedAt).toLocaleDateString()}</p>
              )}
            </div>
          ))}
        </div>
      )}
    </>
  )
}
