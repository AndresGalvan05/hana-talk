import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ExercisePractice } from '../components/ExercisePractice'
import { VocabularyTable } from '../components/VocabularyTable'
import { GrammarPointCard } from '../components/GrammarPointCard'
import { DialogueBox } from '../components/DialogueBox'
import { CultureNoteAside } from '../components/CultureNoteAside'
import { api } from '../api/client'
import type { CourseProgress, Lesson, VocabularyItem } from '../api/types'

export function LessonPage() {
  const { courseId, lessonId } = useParams()
  const [lesson, setLesson] = useState<Lesson | null>(null)
  const [vocabulary, setVocabulary] = useState<VocabularyItem[]>([])
  const [siblings, setSiblings] = useState<Lesson[]>([])
  const [error, setError] = useState<string | null>(null)
  const [completing, setCompleting] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [progress, setProgress] = useState<CourseProgress | null>(null)
  const [indexOpen, setIndexOpen] = useState(false)

  useEffect(() => {
    let cancelled = false
    setIndexOpen(false)
    api
      .get<Lesson>(`/api/courses/${courseId}/lessons/${lessonId}`)
      .then((data) => {
        if (!cancelled) setLesson(data)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load this lesson.')
      })
    api
      .get<VocabularyItem[]>(`/api/lessons/${lessonId}/vocabulary`)
      .then((data) => {
        if (!cancelled) setVocabulary(data)
      })
      .catch(() => {
        /* non-fatal: the rest of the lesson still renders without vocabulary */
      })
    api
      .get<Lesson[]>(`/api/courses/${courseId}/lessons`)
      .then((data) => {
        if (!cancelled) setSiblings([...data].sort((a, b) => a.position - b.position))
      })
      .catch(() => {
        /* non-fatal: prev/next nav and the lesson index just won't render */
      })
    api
      .get<CourseProgress>(`/api/courses/${courseId}/progress`)
      .then((data) => {
        if (cancelled) return
        setProgress(data)
        if (lessonId && data.completedLessonIds.includes(lessonId)) setCompleted(true)
      })
      .catch(() => {
        /* non-fatal: the complete button still works */
      })
    return () => {
      cancelled = true
    }
  }, [courseId, lessonId])

  const currentIndex = siblings.findIndex((l) => l.id === lessonId)
  const prevLesson = currentIndex > 0 ? siblings[currentIndex - 1] : null
  const nextLesson =
    currentIndex >= 0 && currentIndex < siblings.length - 1 ? siblings[currentIndex + 1] : null

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
      <div className="lesson-nav-header">
        <Link to={`/courses/${courseId}`} className="muted">
          ← Back to course
        </Link>
        {siblings.length > 0 && (
          <button type="button" className="link-button" onClick={() => setIndexOpen((v) => !v)}>
            {indexOpen ? 'Hide lessons' : 'Jump to lesson'}
          </button>
        )}
      </div>
      {indexOpen && siblings.length > 0 && (
        <ol className="lesson-list">
          {siblings.map((l) => {
            const done = progress?.completedLessonIds.includes(l.id) ?? false
            const isCurrent = l.id === lessonId
            return (
              <li key={l.id}>
                <Link
                  to={`/courses/${courseId}/lessons/${l.id}`}
                  className={isCurrent ? 'card lesson-row lesson-row-current' : 'card lesson-row'}
                >
                  <span className={done ? 'lesson-position lesson-done' : 'lesson-position'}>
                    {done ? '✓' : l.position}
                  </span>
                  <span>{l.title}</span>
                </Link>
              </li>
            )
          })}
        </ol>
      )}
      {error && <p className="error">{error}</p>}
      {lesson && (
        <article className="card lesson-content">
          <h1>{lesson.title}</h1>
          <VocabularyTable items={vocabulary} />
          <div className="grammar-points">
            {lesson.content.grammarPoints.map((point, index) => (
              <GrammarPointCard key={point.title} point={point} index={index + 1} />
            ))}
          </div>
          <DialogueBox dialogue={lesson.content.dialogue} />
          <CultureNoteAside note={lesson.content.cultureNote} />
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
      {(prevLesson || nextLesson) && (
        <div className="lesson-prev-next">
          {prevLesson ? (
            <Link
              to={`/courses/${courseId}/lessons/${prevLesson.id}`}
              className="card lesson-nav-link"
            >
              ← {prevLesson.title}
            </Link>
          ) : (
            <span />
          )}
          {nextLesson ? (
            <Link
              to={`/courses/${courseId}/lessons/${nextLesson.id}`}
              className="card lesson-nav-link lesson-nav-next"
            >
              {nextLesson.title} →
            </Link>
          ) : (
            <span />
          )}
        </div>
      )}
      {lesson && lessonId && <ExercisePractice lessonId={lessonId} onCompleted={refreshCompletion} />}
    </>
  )
}
