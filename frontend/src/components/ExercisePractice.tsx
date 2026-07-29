import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AttemptResult, Exercise } from '../api/types'

const SLOW_LOADING_DELAY_MS = 4000

interface ExerciseCardProps {
  exercise: Exercise
  onCorrect: () => void
}

function SentenceOrderingInput({
  options,
  picked,
  onChange,
}: {
  options: string[]
  picked: string[]
  onChange: (picked: string[]) => void
}) {
  // Tokens can repeat (e.g. です appearing twice), so track availability by
  // removing one matching token per pick rather than filtering by value.
  const remaining = [...options]
  for (const token of picked) {
    const idx = remaining.indexOf(token)
    if (idx !== -1) remaining.splice(idx, 1)
  }

  return (
    <div className="sentence-ordering">
      <div className="sentence-ordering-picked">
        {picked.length === 0 && <span className="muted">Click words below in order…</span>}
        {picked.map((token, index) => (
          <button
            type="button"
            key={`${token}-${index}`}
            className="token token-picked"
            onClick={() => onChange(picked.filter((_, i) => i !== index))}
          >
            {token}
          </button>
        ))}
      </div>
      <div className="sentence-ordering-available">
        {remaining.map((token, index) => (
          <button
            type="button"
            key={`${token}-${index}`}
            className="token"
            onClick={() => onChange([...picked, token])}
          >
            {token}
          </button>
        ))}
      </div>
    </div>
  )
}

function ExerciseCard({ exercise, onCorrect }: ExerciseCardProps) {
  const [answer, setAnswer] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<boolean | null>(null)

  const isSentenceOrdering = exercise.type === 'SENTENCE_ORDERING'
  const finalAnswer = isSentenceOrdering ? picked.join(' ') : answer

  async function submit() {
    setSubmitting(true)
    try {
      const { correct } = await api.post<AttemptResult>(`/api/exercises/${exercise.id}/attempts`, {
        answer: finalAnswer,
      })
      setResult(correct)
      if (correct) onCorrect()
    } catch {
      setResult(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="exercise-card">
      <p className="exercise-prompt">{exercise.prompt}</p>
      {isSentenceOrdering && exercise.options ? (
        <SentenceOrderingInput options={exercise.options} picked={picked} onChange={setPicked} />
      ) : exercise.options ? (
        <div className="exercise-options">
          {exercise.options.map((option) => (
            <label key={option} className="exercise-option">
              <input
                type="radio"
                name={exercise.id}
                value={option}
                checked={answer === option}
                onChange={() => setAnswer(option)}
              />
              {option}
            </label>
          ))}
        </div>
      ) : (
        <input
          type="text"
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="Type your answer"
        />
      )}
      <button type="button" onClick={submit} disabled={submitting || !finalAnswer}>
        {submitting ? 'Checking…' : 'Submit'}
      </button>
      {result === true && <p className="success">✅ Correct!</p>}
      {result === false && <p className="error">❌ Not quite, try again.</p>}
    </div>
  )
}

interface ExercisePracticeProps {
  lessonId: string
  onCompleted: () => void
}

export function ExercisePractice({ lessonId, onCompleted }: ExercisePracticeProps) {
  const [exercises, setExercises] = useState<Exercise[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [slow, setSlow] = useState(false)

  function load() {
    setError(null)
    setExercises(null)
    setSlow(false)
    const slowTimer = setTimeout(() => setSlow(true), SLOW_LOADING_DELAY_MS)
    api
      .get<Exercise[]>(`/api/lessons/${lessonId}/exercises`)
      .then((data) => setExercises(data))
      .catch(() => setError('Could not load exercises for this lesson.'))
      .finally(() => clearTimeout(slowTimer))
  }

  useEffect(load, [lessonId])

  if (error) {
    return (
      <section className="card exercise-practice">
        <h2>Practice exercises</h2>
        <p className="error">{error}</p>
        <button type="button" onClick={load}>
          Try again
        </button>
      </section>
    )
  }

  if (!exercises) {
    return (
      <section className="card exercise-practice">
        <h2>Practice exercises</h2>
        <p className="muted">
          {slow ? 'Still generating — this can take up to a minute the first time.' : 'Loading exercises…'}
        </p>
      </section>
    )
  }

  if (exercises.length === 0) {
    return null
  }

  return (
    <section className="card exercise-practice">
      <h2>Practice exercises</h2>
      {exercises.map((exercise) => (
        <ExerciseCard key={exercise.id} exercise={exercise} onCorrect={onCompleted} />
      ))}
    </section>
  )
}
