import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { VocabularyItem } from '../api/types'

export function FlashcardsPage() {
  const [queue, setQueue] = useState<VocabularyItem[] | null>(null)
  const [revealed, setRevealed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    api
      .get<VocabularyItem[]>('/api/vocabulary/review')
      .then(setQueue)
      .catch(() => setError('Could not load your review queue.'))
  }, [])

  async function markResult(correct: boolean) {
    if (!queue || queue.length === 0 || submitting) return
    const current = queue[0]
    setSubmitting(true)
    setError(null)
    try {
      await api.post(`/api/vocabulary-items/${current.id}/review`, { correct })
      setQueue(queue.slice(1))
      setRevealed(false)
    } catch {
      setError('Could not record that review. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  const current = queue?.[0] ?? null

  return (
    <>
      <h1>Flashcards</h1>
      <p className="muted">Review vocabulary from lessons you've completed.</p>
      {error && <p className="error">{error}</p>}
      {queue === null && !error && <p className="muted">Loading…</p>}
      {queue !== null && current === null && (
        <p className="muted">Nothing due right now — check back later.</p>
      )}
      {current && (
        <div className="card flashcard">
          <p className="flashcard-japanese">{current.japanese}</p>
          {revealed && (
            <>
              <p className="flashcard-reading muted">{current.reading}</p>
              <p className="flashcard-meaning">{current.meaning}</p>
            </>
          )}
          {!revealed ? (
            <button type="button" onClick={() => setRevealed(true)}>
              Reveal
            </button>
          ) : (
            <div className="flashcard-actions">
              <button
                type="button"
                className="flashcard-incorrect"
                onClick={() => markResult(false)}
                disabled={submitting}
              >
                Didn't know it
              </button>
              <button type="button" onClick={() => markResult(true)} disabled={submitting}>
                Got it
              </button>
            </div>
          )}
        </div>
      )}
    </>
  )
}
