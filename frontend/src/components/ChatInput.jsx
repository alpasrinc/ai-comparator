import { useState } from 'react'

function ChatInput({ onSend }) {
  const [message, setMessage] = useState('')

  const trimmedMessage = message.trim()
  const isEmpty = trimmedMessage.length === 0

  function handleSubmit(event) {
    event.preventDefault()

    if (isEmpty) {
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
      />

      <div className="chat-input__footer">
        <small>Enter: gönder · Shift + Enter: yeni satır</small>

        <button type="submit" disabled={isEmpty}>
          Gönder
        </button>
      </div>
    </form>
  )
}

export default ChatInput