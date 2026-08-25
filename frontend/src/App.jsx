import { useEffect, useState } from 'react'
import AiPanel from './components/AiPanel'
import ChatInput from './components/ChatInput'
import ConversationSidebar from './components/ConversationSidebar'
import {
  compareMessage,
  getConversation,
  getConversations,
  getHealth,
  selectActiveMessage,
} from './services/api'
import './App.css'

const PROVIDERS = ['OPENAI', 'ANTHROPIC', 'GEMINI']

function App() {
  const [backendStatus, setBackendStatus] = useState('Kontrol ediliyor...')
  const [backendError, setBackendError] = useState('')
  const [conversations, setConversations] = useState([])
  const [isLoadingConversations, setIsLoadingConversations] = useState(true)
  const [isOpeningConversation, setIsOpeningConversation] = useState(false)
  const [historyError, setHistoryError] = useState('')
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

    getConversations()
      .then((data) => {
        setConversations(data)
      })
      .catch((requestError) => {
        setHistoryError(requestError.message)
      })
      .finally(() => {
        setIsLoadingConversations(false)
      })
  }, [])

  async function refreshConversations() {
    try {
      const data = await getConversations()
      setConversations(data)
      setHistoryError('')
    } catch (requestError) {
      setHistoryError(requestError.message)
    }
  }

  async function handleOpenConversation(selectedConversationId) {
    setIsOpeningConversation(true)
    setHistoryError('')
    setComparisonError('')
    setSelectionError('')

    try {
      const conversation = await getConversation(selectedConversationId)

      const latestUserMessage = [...conversation.messages]
        .reverse()
        .find((message) => message.role === 'USER')

      const latestResponses = latestUserMessage
        ? conversation.messages
            .filter(
              (message) =>
                message.role === 'ASSISTANT' &&
                message.parentMessageId === latestUserMessage.id,
            )
            .map((message) => ({
              messageId: message.id,
              provider: message.provider,
              content: message.content,
            }))
        : []

      const activeMessage = conversation.messages.find(
        (message) => message.id === conversation.activeMessageId,
      )

      setConversationId(conversation.id)
      setSubmittedMessage(latestUserMessage?.content ?? '')
      setResponses(latestResponses)
      setSelectedMessageId(conversation.activeMessageId)
      setSelectedProvider(activeMessage?.provider ?? '')
    } catch (requestError) {
      setHistoryError(requestError.message)
    } finally {
      setIsOpeningConversation(false)
    }
  }

  function handleNewConversation() {
    setConversationId(null)
    setSubmittedMessage('')
    setResponses([])
    setSelectedMessageId(null)
    setSelectedProvider('')
    setComparisonError('')
    setSelectionError('')
    setHistoryError('')
  }

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
      await refreshConversations()
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
      await refreshConversations()
    } catch (requestError) {
      setSelectionError(requestError.message)
    } finally {
      setSelectingMessageId(null)
    }
  }

  const selectedCurrentResponse = responses.some(
    (response) => response.messageId === selectedMessageId,
  )

  const mustSelectResponse =
    responses.length > 0 && !selectedCurrentResponse

  const inputDisabled =
    isLoading ||
    isOpeningConversation ||
    selectingMessageId !== null ||
    mustSelectResponse

  return (
    <div className="app-layout">
      <ConversationSidebar
        conversations={conversations}
        activeConversationId={conversationId}
        isLoading={isLoadingConversations}
        onSelect={handleOpenConversation}
        onNewConversation={handleNewConversation}
      />

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

        {historyError && (
          <div className="selection-notice selection-notice--error">
            Geçmiş yüklenemedi: {historyError}
          </div>
        )}

        {isOpeningConversation && (
          <div className="selection-notice">
            Konuşma geçmişi yükleniyor...
          </div>
        )}

        {submittedMessage && (
          <div className="submitted-message">
            <span>Son kullanıcı mesajı</span>
            <p>{submittedMessage}</p>
          </div>
        )}

        {mustSelectResponse && !isLoading && (
          <div className="selection-notice">
            Devam etmek için aşağıdaki AI cevaplarından birini seçin.
          </div>
        )}

        {selectedCurrentResponse && (
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
    </div>
  )
}

export default App