import type { Dialogue } from '../api/types'

interface DialogueBoxProps {
  dialogue: Dialogue
}

export function DialogueBox({ dialogue }: DialogueBoxProps) {
  return (
    <section className="dialogue-box">
      <h2>{dialogue.title}</h2>
      <ul className="dialogue-lines">
        {dialogue.lines.map((line, index) => (
          <li key={`${index}-${line.speaker}`}>
            <span className="dialogue-speaker">{line.speaker}</span>
            <span lang="ja" className="dialogue-japanese">
              {line.japanese}
            </span>
            <span className="muted dialogue-english">{line.english}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
