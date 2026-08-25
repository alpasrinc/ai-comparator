import { useEffect, useState } from 'react'
import AiPanel from './components/AiPanel'
import ChatInput from './components/ChatInput'
import {
  compareMessage,
  getHealth,
  selectActiveMessage,
} from './services/api'
import './App.css'

const PROVIDERS = ['OPENAI', 'ANTHROPIC', 'GEMINI']

function App() {
  const [backendStatus, setBackendStatus] = useState('Kontrol ediliyor...')
  const [backendError, setBackendError] = useState('')
  const [conversationId, setConversationId] = useState(null)
  const [submittedMessage, setSubmittedMessage] = useState('')
  const [responses, setResponses] = useState([])
  const [selectedMessageId, setSelectedMessageId] = useState(null)
  const [selectedProvider, setSelectedProvider] = useState('')
  const [selectingMessageId, setSelectingMessageId] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [comparisonError, setComparisonError] = useState('')
  const [selectionError, setSelectionError] = useState('')

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
    setSelectedMessageId(null)
    setSelectedProvider('')
    setComparisonError('')
    setSelectionError('')
    setIsLoading(true)

    try {
      const data = await compareMessage(message, conversationId)

      setConversationId(data.conversationId)
      setResponses(data.responses)
    } catch (requestError) {
      setComparisonError(requestError.message)
    } finally {
      setIsLoading(false)
    }
  }

  async function handleSelect(response) {
    if (!conversationId || !response.messageId) {
      return
    }

    setSelectingMessageId(response.messageId)
    setSelectionError('')

    try {
      const result = await selectActiveMessage(
        conversationId,
        response.messageId,
      )

      setSelectedMessageId(result.activeMessageId)
      setSelectedProvider(result.provider)
    } catch (requestError) {
      setSelectionError(requestError.message)
    } finally {
      setSelectingMessageId(null)
    }
  }

  const mustSelectResponse =
    responses.length > 0 && selectedMessageId === null

  const inputDisabled =
    isLoading || selectingMessageId !== null || mustSelectResponse

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

      {mustSelectResponse && !isLoading && (
        <div className="selection-notice">
          Devam etmek için aşağıdaki AI cevaplarından birini seçin.
        </div>
      )}

      {selectedMessageId && (
        <div className="selection-notice selection-notice--success">
          {selectedProvider} cevabı seçildi. Yeni mesajınız bu dal üzerinden
          devam edecek.
        </div>
      )}

      {selectionError && (
        <div className="selection-notice selection-notice--error">
          {selectionError}
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
              response={providerResponse}
              isLoading={isLoading}
              error={comparisonError}
              isSelected={
                selectedMessageId === providerResponse?.messageId
              }
              isSelecting={
                selectingMessageId === providerResponse?.messageId
              }
              onSelect={handleSelect}
            />
          )
        })}
      </section>

      <ChatInput
  onSend={handleSend}
  disabled={inputDisabled}
  isLoading={isLoading}
 />
    </main>
  )
}

export default App