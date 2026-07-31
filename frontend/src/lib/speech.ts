export function isSpeechSupported(): boolean {
  return 'speechSynthesis' in window
}

export function speak(text: string): void {
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'ja-JP'
  utterance.rate = 0.85
  window.speechSynthesis.speak(utterance)
}
