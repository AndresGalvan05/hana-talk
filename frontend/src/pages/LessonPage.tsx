import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ExercisePractice } from '../components/ExercisePractice'
import { api } from '../api/client'
import type { CourseProgress, Lesson } from '../api/types'

export function LessonPage() {
  const { courseId, lessonId } = useParams()
  const [lesson, setLesson] = useState<Lesson | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [completing, setCompleting] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [progress, setProgress] = useState<CourseProgress | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .get<Lesson>(`/api/courses/${courseId}/lessons/${lessonId}`)
      .then((data) => {
        if (!cancelled) setLesson(data)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load this lesson.')
      })
    api
      .get<CourseProgress>(`/api/courses/${courseId}/progress`)
      .then((data) => {
        if (!cancelled && lessonId && data.completedLessonIds.includes(lessonId)) {
          setCompleted(true)
          setProgress(data)
        }
      })
      .catch(() => {
        /* non-fatal: the complete button still works */
      })
    return () => {
      cancelled = true
    }
  }, [courseId, lessonId])

  // Shared by the manual "Mark as complete" button and a correct exercise
  // attempt — one place flips the lesson into "completed" UI regardless of
  // which path triggered it.
  async function refreshCompletion() {
    setCompleted(true)
    try {
      setProgress(await api.get<CourseProgress>(`/api/courses/${courseId}/progress`))
    } catch {
      /* non-fatal: the completion banner still shows without the progress line */
    }
  }

  async function markComplete() {
    setCompleting(true)
    setError(null)
    try {
      await api.post<void>(`/api/courses/${courseId}/lessons/${lessonId}/complete`)
      await refreshCompletion()
    } catch {
      setError('Could not mark the lesson complete. Try again.')
    } finally {
      setCompleting(false)
    }
  }

  return (
    <>
      <p>
        <Link to={`/courses/${courseId}`} className="muted">
          ← Back to course
        </Link>
      </p>
      {error && <p className="error">{error}</p>}
      {lesson && (
        <article className="card lesson-content">
          <h1>{lesson.title}</h1>
          <pre className="lesson-text">{lesson.content}</pre>
          {completed ? (
            <p className="success">
              ✅ Lesson completed!
              {progress && ` Course progress: ${progress.completed} / ${progress.total}.`}
            </p>
          ) : (
            <button type="button" onClick={markComplete} disabled={completing}>
              {completing ? 'Saving…' : 'Mark as complete'}
            </button>
          )}
        </article>
      )}
      {lesson && lessonId && <ExercisePractice lessonId={lessonId} onCompleted={refreshCompletion} />}
    </>
  )
}
