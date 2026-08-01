import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ConfirmDeleteButton } from '../components/ConfirmDeleteButton'
import { api } from '../api/client'
import type { Course } from '../api/types'

export function AdminCoursesPage() {
  const [courses, setCourses] = useState<Course[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    setError(null)
    api
      .get<Course[]>('/api/courses')
      .then(setCourses)
      .catch(() => setError('Could not load courses.'))
  }, [])

  useEffect(load, [load])

  const deleteCourse = (id: string) => {
    api
      .delete(`/api/courses/${id}`)
      .then(load)
      .catch(() => setError('Could not delete that course.'))
  }

  return (
    <>
      <h1>Admin: Courses</h1>
      {error && <p className="error">{error}</p>}
      <p>
        <Link to="/admin/courses/new">New course</Link>
      </p>
      {courses !== null && (
        <ul className="admin-list">
          {courses.map((course) => (
            <li key={course.id} className="card admin-list-row">
              <span className="badge">{course.jlptLevel}</span>
              <span className="admin-list-title">{course.title}</span>
              <Link to={`/admin/courses/${course.id}/lessons`}>Lessons</Link>
              <Link to={`/admin/courses/${course.id}/edit`}>Edit</Link>
              <ConfirmDeleteButton label="Delete" onConfirm={() => deleteCourse(course.id)} />
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
