import { useState } from 'react'

function ChatInput({ onSend, disabled = false }) {
  const [message, setMessage] = useState('')

  const trimmedMessage = message.trim()
  const isEmpty = trimmedMessage.length === 0
  const cannotSend = isEmpty || disabled

  function handleSubmit(event) {
    event.preventDefault()

    if (cannotSend) {
      return
    }

    onSend(trimmedMessage)
    setMessage('')
  }

  function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form.requestSubmit()
    }
  }

  return (
    <form className="chat-input" onSubmit={handleSubmit}>
      <label htmlFor="message">Mesajınız</label>

      <textarea
        id="message"
        name="message"
        value={message}
        onChange={(event) => setMessage(event.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Bir şey sorun..."
        rows="4"
        maxLength="4000"
        disabled={disabled}
      />

      <div className="chat-input__footer">
        <small>Enter: gönder · Shift + Enter: yeni satır</small>

        <button type="submit" disabled={cannotSend}>
          {disabled ? 'Cevaplar bekleniyor...' : 'Gönder'}
        </button>
      </div>
    </form>
  )
}

export default ChatInput