import { useEffect, useState } from 'react'
import ChatInput from './components/ChatInput'
import './App.css'

function App() {
  const [backendStatus, setBackendStatus] = useState('Kontrol ediliyor...')
  const [backendError, setBackendError] = useState('')
  const [submittedMessage, setSubmittedMessage] = useState('')

  useEffect(() => {
    fetch('http://localhost:8080/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }

        return response.json()
      })
      .then((data) => {
        setBackendStatus(data.status)
      })
      .catch((requestError) => {
        setBackendError(requestError.message)
        setBackendStatus('Bağlantı kurulamadı')
      })
  }, [])

  function handleSend(message) {
    setSubmittedMessage(message)
  }

  return (
    <main className="app">
      <header className="app__header">
        <div>
          <p className="app__eyebrow">AI COMPARATOR</p>
          <h1>Yapay zekâ cevaplarını karşılaştırın</h1>
          <p className="app__description">
            Tek mesaj yazın, farklı yapay zekâların cevaplarını aynı ekranda
            inceleyin.
          </p>
        </div>

        <div
          className={`backend-status ${
            backendError ? 'backend-status--error' : ''
          }`}
        >
          <span className="backend-status__indicator" />
          Backend: {backendStatus}
        </div>
      </header>

      <section className="workspace">
        {submittedMessage ? (
          <div className="submitted-message">
            <span>Son gönderilen mesaj</span>
            <p>{submittedMessage}</p>
          </div>
        ) : (
          <div className="empty-state">
            <h2>Karşılaştırmaya hazır</h2>
            <p>İlk mesajınızı aşağıdaki alana yazın.</p>
          </div>
        )}
      </section>

      <ChatInput onSend={handleSend} />
    </main>
  )
}

export default App