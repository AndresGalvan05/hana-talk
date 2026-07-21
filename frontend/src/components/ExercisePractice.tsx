import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AttemptResult, Exercise } from '../api/types'

const SLOW_LOADING_DELAY_MS = 4000

interface ExerciseCardProps {
  exercise: Exercise
  onCorrect: () => void
}

function ExerciseCard({ exercise, onCorrect }: ExerciseCardProps) {
  const [answer, setAnswer] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<boolean | null>(null)

  async function submit() {
    setSubmitting(true)
    try {
      const { correct } = await api.post<AttemptResult>(`/api/exercises/${exercise.id}/attempts`, {
        answer,
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
      {exercise.options ? (
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
      <button type="button" onClick={submit} disabled={submitting || !answer}>
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
