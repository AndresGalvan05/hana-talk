import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { JlptLevel, Streak, UserProfile } from '../api/types'

const LEVELS: JlptLevel[] = ['N5', 'N4', 'N3', 'N2', 'N1']

export function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [streak, setStreak] = useState<Streak | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectedLevel, setSelectedLevel] = useState<JlptLevel>('N5')
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    setError(null)
    Promise.all([api.get<UserProfile>('/api/users/me'), api.get<Streak>('/api/users/me/streak')])
      .then(([p, s]) => {
        setProfile(p)
        setStreak(s)
        setSelectedLevel(p.startingLevel ?? 'N5')
      })
      .catch(() => setError('Could not load your profile.'))
  }, [])

  useEffect(load, [load])

  const saveLevel = () => {
    setSaving(true)
    api
      .patch<UserProfile>('/api/users/me/level', { level: selectedLevel })
      .then((p) => setProfile(p))
      .catch(() => setError('Could not update your level.'))
      .finally(() => setSaving(false))
  }

  return (
    <>
      <h1>Profile</h1>
      {error && <p className="error">{error}</p>}
      {profile && (
        <div className="card profile-card">
          <p>
            <span className="muted">Username</span>
            <br />
            {profile.username}
          </p>
          <p>
            <span className="muted">Current streak</span>
            <br />
            {streak ? `🔥 ${streak.currentStreak} day${streak.currentStreak === 1 ? '' : 's'}` : '…'}
          </p>
          <label htmlFor="level-select">JLPT level</label>
          <select
            id="level-select"
            value={selectedLevel}
            onChange={(e) => setSelectedLevel(e.target.value as JlptLevel)}
          >
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
          <button type="button" onClick={saveLevel} disabled={saving}>
            {saving ? 'Saving…' : 'Save level'}
          </button>
        </div>
      )}
      <p>
        <Link to="/leaderboard">See the leaderboard →</Link>
      </p>
    </>
  )
}
