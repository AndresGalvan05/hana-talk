import { useState } from 'react'
import { api } from '../api/client'
import type { ChatMessage, ChatReply } from '../api/types'

interface Turn extends ChatMessage {
  english?: string
  correction?: string | null
}

export function ChatPage() {
  const [messages, setMessages] = useState<Turn[]>([])
  const [input, setInput] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function send() {
    const messageText = input.trim()
    if (!messageText || pending !== null) return

    const history: ChatMessage[] = messages.map(({ speaker, japanese }) => ({ speaker, japanese }))
    setPending(messageText)
    setInput('')
    setError(null)
    try {
      const reply = await api.post<ChatReply>('/api/conversation/reply', {
        history,
        message: messageText,
      })
      setMessages((prev) => [
        ...prev,
        { speaker: 'user', japanese: messageText },
        { speaker: 'tutor', japanese: reply.japanese, english: reply.english, correction: reply.correction },
      ])
    } catch {
      setError('Could not get a reply. Try again.')
      setInput(messageText)
    } finally {
      setPending(null)
    }
  }

  return (
    <>
      <h1>Chat practice</h1>
      <p className="muted">Practice a conversation in Japanese with an AI tutor.</p>
      <div className="card chat-transcript">
        {messages.length === 0 && pending === null && (
          <p className="muted">Send a message to start the conversation.</p>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            className={m.speaker === 'user' ? 'chat-bubble chat-bubble-user' : 'chat-bubble chat-bubble-tutor'}
          >
            <p className="chat-japanese">{m.japanese}</p>
            {m.english && <p className="chat-english muted">{m.english}</p>}
            {m.correction && <p className="chat-correction">📝 {m.correction}</p>}
          </div>
        ))}
        {pending !== null && (
          <div className="chat-bubble chat-bubble-user chat-bubble-pending">
            <p className="chat-japanese">{pending}</p>
            <p className="muted">…</p>
          </div>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      <div className="chat-input-row">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') send()
          }}
          placeholder="日本語でメッセージを送ってください…"
          aria-label="Message in Japanese"
          disabled={pending !== null}
        />
        <button type="button" onClick={send} disabled={pending !== null || !input.trim()}>
          {pending !== null ? 'Sending…' : 'Send'}
        </button>
      </div>
    </>
  )
}
