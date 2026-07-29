import type { VocabularyItem } from '../api/types'

interface VocabularyTableProps {
  items: VocabularyItem[]
}

export function VocabularyTable({ items }: VocabularyTableProps) {
  if (items.length === 0) return null

  return (
    <section className="vocabulary-table">
      <h2>Vocabulary</h2>
      <table>
        <thead>
          <tr>
            <th>Japanese</th>
            <th>Reading</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td lang="ja">{item.japanese}</td>
              <td className="muted">{item.reading}</td>
              <td>{item.meaning}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
