import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Course, JlptLevel } from '../api/types'

const LEVELS: JlptLevel[] = ['N5', 'N4', 'N3', 'N2', 'N1']

export function AdminCourseFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = id !== undefined
  const navigate = useNavigate()

  const [title, setTitle] = useState('')
  const [jlptLevel, setJlptLevel] = useState<JlptLevel>('N5')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    api
      .get<Course>(`/api/courses/${id}`)
      .then((course) => {
        setTitle(course.title)
        setJlptLevel(course.jlptLevel)
        setDescription(course.description ?? '')
      })
      .catch(() => setError('Could not load that course.'))
  }, [id, isEdit])

  const save = () => {
    setSaving(true)
    setError(null)
    const body = { title, jlptLevel, description: description || null }
    const request = isEdit ? api.put<Course>(`/api/courses/${id}`, body) : api.post<Course>('/api/courses', body)
    request
      .then(() => navigate('/admin/courses'))
      .catch(() => setError('Could not save that course.'))
      .finally(() => setSaving(false))
  }

  return (
    <>
      <h1>{isEdit ? 'Edit course' : 'New course'}</h1>
      {error && <p className="error">{error}</p>}
      <div className="card admin-form">
        <label htmlFor="course-title">Title</label>
        <input id="course-title" type="text" value={title} onChange={(e) => setTitle(e.target.value)} />

        <label htmlFor="course-level">JLPT level</label>
        <select id="course-level" value={jlptLevel} onChange={(e) => setJlptLevel(e.target.value as JlptLevel)}>
          {LEVELS.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>

        <label htmlFor="course-description">Description</label>
        <textarea
          id="course-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
        />

        <button type="button" onClick={save} disabled={saving || !title.trim()}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </>
  )
}
