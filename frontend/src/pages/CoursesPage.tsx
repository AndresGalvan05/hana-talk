import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Course, JlptLevel } from '../api/types'

const LEVELS: JlptLevel[] = ['N5', 'N4', 'N3', 'N2', 'N1']

export function CoursesPage() {
  const [level, setLevel] = useState<JlptLevel | null>(null)
  const [courses, setCourses] = useState<Course[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setCourses(null)
    setError(null)
    api
      .get<Course[]>(level ? `/api/courses?jlptLevel=${level}` : '/api/courses')
      .then((data) => {
        if (!cancelled) setCourses(data)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load courses.')
      })
    return () => {
      cancelled = true
    }
  }, [level])

  return (
    <>
      <h1>Courses</h1>
      <div className="level-filter">
        <button
          type="button"
          className={level === null ? 'chip chip-active' : 'chip'}
          onClick={() => setLevel(null)}
        >
          All
        </button>
        {LEVELS.map((l) => (
          <button
            key={l}
            type="button"
            className={level === l ? 'chip chip-active' : 'chip'}
            onClick={() => setLevel(l)}
          >
            {l}
          </button>
        ))}
      </div>
      {error && <p className="error">{error}</p>}
      {courses === null && !error && <p className="muted">Loading…</p>}
      {courses !== null && courses.length === 0 && (
        <p className="muted">No courses for this level yet.</p>
      )}
      <div className="course-grid">
        {courses?.map((course) => (
          <Link key={course.id} to={`/courses/${course.id}`} className="card course-card">
            <span className="badge">{course.jlptLevel}</span>
            <h2>{course.title}</h2>
            {course.description && <p className="muted">{course.description}</p>}
          </Link>
        ))}
      </div>
    </>
  )
}
