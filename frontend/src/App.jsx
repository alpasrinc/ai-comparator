import { useEffect, useState } from 'react'
import AiPanel from './components/AiPanel'
import ChatInput from './components/ChatInput'
import { compareMessage, getHealth } from './services/api'
import './App.css'

const PROVIDERS = ['OPENAI', 'ANTHROPIC', 'GEMINI']

function App() {
  const [backendStatus, setBackendStatus] = useState('Kontrol ediliyor...')
  const [backendError, setBackendError] = useState('')
  const [submittedMessage, setSubmittedMessage] = useState('')
  const [responses, setResponses] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [comparisonError, setComparisonError] = useState('')

  useEffect(() => {
    getHealth()
      .then((data) => {
        setBackendStatus(data.status)
      })
      .catch((requestError) => {
        setBackendError(requestError.message)
        setBackendStatus('Bağlantı kurulamadı')
      })
  }, [])

  async function handleSend(message) {
    setSubmittedMessage(message)
    setResponses([])
    setComparisonError('')
    setIsLoading(true)

    try {
      const data = await compareMessage(message)
      setResponses(data.responses)
    } catch (requestError) {
      setComparisonError(requestError.message)
    } finally {
      setIsLoading(false)
    }
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

      {submittedMessage && (
        <div className="submitted-message">
          <span>Gönderilen mesaj</span>
          <p>{submittedMessage}</p>
        </div>
      )}

      <section className="ai-grid" aria-label="Yapay zekâ cevapları">
        {PROVIDERS.map((provider) => {
          const providerResponse = responses.find(
            (response) => response.provider === provider,
          )

          return (
            <AiPanel
              key={provider}
              provider={provider}
              response={providerResponse?.content ?? ''}
              isLoading={isLoading}
              error={comparisonError}
            />
          )
        })}
      </section>

      <ChatInput onSend={handleSend} disabled={isLoading} />
    </main>
  )
}

export default App