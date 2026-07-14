import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Course, CourseProgress, Lesson } from '../api/types'

export function CourseDetailPage() {
  const { courseId } = useParams()
  const [course, setCourse] = useState<Course | null>(null)
  const [lessons, setLessons] = useState<Lesson[] | null>(null)
  const [progress, setProgress] = useState<CourseProgress | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    if (!courseId) return
    setError(null)
    Promise.all([
      api.get<Course>(`/api/courses/${courseId}`),
      api.get<Lesson[]>(`/api/courses/${courseId}/lessons`),
      api.get<CourseProgress>(`/api/courses/${courseId}/progress`),
    ])
      .then(([c, ls, p]) => {
        setCourse(c)
        setLessons(ls)
        setProgress(p)
      })
      .catch(() => setError('Could not load this course.'))
  }, [courseId])

  useEffect(load, [load])

  const pct =
    progress && progress.total > 0 ? Math.round((progress.completed / progress.total) * 100) : 0

  return (
    <>
      <p>
        <Link to="/courses" className="muted">
          ← All courses
        </Link>
      </p>
      {error && <p className="error">{error}</p>}
      {course && (
        <>
          <span className="badge">{course.jlptLevel}</span>
          <h1>{course.title}</h1>
          {course.description && <p className="muted">{course.description}</p>}
          {progress && (
            <div className="progress-block">
              <div className="progress-track">
                <div className="progress-fill" style={{ width: `${pct}%` }} />
              </div>
              <span className="muted">
                {progress.completed} / {progress.total} lessons completed ({pct}%)
              </span>
            </div>
          )}
          <ol className="lesson-list">
            {lessons?.map((lesson) => (
              <li key={lesson.id}>
                <Link to={`/courses/${course.id}/lessons/${lesson.id}`} className="card lesson-row">
                  <span className="lesson-position">{lesson.position}</span>
                  <span>{lesson.title}</span>
                </Link>
              </li>
            ))}
          </ol>
        </>
      )}
    </>
  )
}
