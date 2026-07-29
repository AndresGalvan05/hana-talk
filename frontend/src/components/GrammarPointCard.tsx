import type { GrammarPoint } from '../api/types'

interface GrammarPointCardProps {
  point: GrammarPoint
  index: number
}

export function GrammarPointCard({ point, index }: GrammarPointCardProps) {
  return (
    <article className="grammar-point-card">
      <h3>
        <span className="grammar-point-index">{index}</span>
        {point.title}
      </h3>
      <p>{point.explanation}</p>
      {point.examples.length > 0 && (
        <ul className="example-list">
          {point.examples.map((example) => (
            <li key={example.japanese}>
              <span lang="ja" className="example-japanese">
                {example.japanese}
              </span>
              <span className="muted example-romaji"> ({example.romaji})</span>
              <span className="example-english"> — {example.english}</span>
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}
