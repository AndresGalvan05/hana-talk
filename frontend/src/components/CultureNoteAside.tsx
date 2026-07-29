import type { CultureNote } from '../api/types'

interface CultureNoteAsideProps {
  note: CultureNote
}

export function CultureNoteAside({ note }: CultureNoteAsideProps) {
  return (
    <aside className="culture-note">
      <h2>💡 {note.title}</h2>
      <p>{note.body}</p>
    </aside>
  )
}
