import { isSpeechSupported, speak } from '../lib/speech'

interface SpeakButtonProps {
  text: string
}

export function SpeakButton({ text }: SpeakButtonProps) {
  if (!isSpeechSupported()) return null

  return (
    <button
      type="button"
      className="speak-button"
      onClick={() => speak(text)}
      aria-label={`Play pronunciation of ${text}`}
    >
      🔊
    </button>
  )
}
