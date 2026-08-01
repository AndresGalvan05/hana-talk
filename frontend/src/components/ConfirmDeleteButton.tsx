import { useState } from 'react'

interface ConfirmDeleteButtonProps {
  label: string
  onConfirm: () => void
}

export function ConfirmDeleteButton({ label, onConfirm }: ConfirmDeleteButtonProps) {
  const [confirming, setConfirming] = useState(false)

  if (confirming) {
    return (
      <span className="confirm-delete">
        <button type="button" className="confirm-delete-confirm" onClick={onConfirm}>
          Confirm
        </button>
        <button type="button" onClick={() => setConfirming(false)}>
          Cancel
        </button>
      </span>
    )
  }

  return (
    <button type="button" className="confirm-delete-trigger" onClick={() => setConfirming(true)}>
      {label}
    </button>
  )
}
