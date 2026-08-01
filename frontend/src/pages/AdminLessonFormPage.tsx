import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Lesson, LessonContent } from '../api/types'

const BLANK_CONTENT: LessonContent = {
  grammarPoints: [],
  dialogue: { title: '', lines: [] },
  cultureNote: { title: '', body: '' },
}

export function AdminLessonFormPage() {
  const { courseId, lessonId } = useParams<{ courseId: string; lessonId: string }>()
  const isEdit = lessonId !== undefined
  const navigate = useNavigate()

  const [title, setTitle] = useState('')
  const [position, setPosition] = useState(1)
  const [contentText, setContentText] = useState(() => JSON.stringify(BLANK_CONTENT, null, 2))
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    api
      .get<Lesson>(`/api/courses/${courseId}/lessons/${lessonId}`)
      .then((lesson) => {
        setTitle(lesson.title)
        setPosition(lesson.position)
        setContentText(JSON.stringify(lesson.content, null, 2))
      })
      .catch(() => setError('Could not load that lesson.'))
  }, [courseId, lessonId, isEdit])

  const save = () => {
    let content: LessonContent
    try {
      content = JSON.parse(contentText)
    } catch {
      setError('Content is not valid JSON.')
      return
    }

    setSaving(true)
    setError(null)
    const body = { title, position, content }
    const request = isEdit
      ? api.put<Lesson>(`/api/courses/${courseId}/lessons/${lessonId}`, body)
      : api.post<Lesson>(`/api/courses/${courseId}/lessons`, body)
    request
      .then(() => navigate(`/admin/courses/${courseId}/lessons`))
      .catch(() => setError('Could not save that lesson.'))
      .finally(() => setSaving(false))
  }

  return (
    <>
      <h1>{isEdit ? 'Edit lesson' : 'New lesson'}</h1>
      {error && <p className="error">{error}</p>}
      <p>
        <Link to={`/admin/courses/${courseId}/lessons`}>← Back to lessons</Link>
      </p>
      <div className="card admin-form">
        <label htmlFor="lesson-title">Title</label>
        <input id="lesson-title" type="text" value={title} onChange={(e) => setTitle(e.target.value)} />

        <label htmlFor="lesson-position">Position</label>
        <input
          id="lesson-position"
          type="number"
          min={1}
          value={position}
          onChange={(e) => setPosition(Number(e.target.value))}
        />

        <label htmlFor="lesson-content">
          Content (JSON — grammarPoints, dialogue, cultureNote)
        </label>
        <textarea
          id="lesson-content"
          className="admin-json-editor"
          value={contentText}
          onChange={(e) => setContentText(e.target.value)}
          rows={20}
          spellCheck={false}
        />

        <button type="button" onClick={save} disabled={saving || !title.trim()}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </>
  )
}
