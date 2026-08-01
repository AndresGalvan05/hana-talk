import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ConfirmDeleteButton } from '../components/ConfirmDeleteButton'
import { api } from '../api/client'
import type { Lesson } from '../api/types'

export function AdminLessonsPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [lessons, setLessons] = useState<Lesson[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    setError(null)
    api
      .get<Lesson[]>(`/api/courses/${courseId}/lessons`)
      .then(setLessons)
      .catch(() => setError('Could not load lessons.'))
  }, [courseId])

  useEffect(load, [load])

  const deleteLesson = (id: string) => {
    api
      .delete(`/api/courses/${courseId}/lessons/${id}`)
      .then(load)
      .catch(() => setError('Could not delete that lesson.'))
  }

  return (
    <>
      <h1>Admin: Lessons</h1>
      {error && <p className="error">{error}</p>}
      <p>
        <Link to="/admin/courses">← Back to courses</Link>
      </p>
      <p>
        <Link to={`/admin/courses/${courseId}/lessons/new`}>New lesson</Link>
      </p>
      {lessons !== null && (
        <ul className="admin-list">
          {lessons
            .slice()
            .sort((a, b) => a.position - b.position)
            .map((lesson) => (
              <li key={lesson.id} className="card admin-list-row">
                <span className="muted">#{lesson.position}</span>
                <span className="admin-list-title">{lesson.title}</span>
                <Link to={`/admin/courses/${courseId}/lessons/${lesson.id}/edit`}>Edit</Link>
                <ConfirmDeleteButton label="Delete" onConfirm={() => deleteLesson(lesson.id)} />
              </li>
            ))}
        </ul>
      )}
    </>
  )
}
