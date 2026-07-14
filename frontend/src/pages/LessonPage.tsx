import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
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
    return () => {
      cancelled = true
    }
  }, [courseId, lessonId])

  async function markComplete() {
    setCompleting(true)
    setError(null)
    try {
      await api.post<void>(`/api/courses/${courseId}/lessons/${lessonId}/complete`)
      setCompleted(true)
      setProgress(await api.get<CourseProgress>(`/api/courses/${courseId}/progress`))
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
    </>
  )
}
