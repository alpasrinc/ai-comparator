import { useEffect, useState } from 'react'
import AiPanel from './components/AiPanel'
import BranchTreePanel from './components/BranchTreePanel'
import ChatInput from './components/ChatInput'
import ConversationSidebar from './components/ConversationSidebar'
import {
  getConversation,
  getConversations,
  getHealth,
  retryProvider,
  selectActiveMessage,
  streamCompareMessage,
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
  const [userMessageId, setUserMessageId] = useState(null)
  const [submittedMessage, setSubmittedMessage] = useState('')
  const [responses, setResponses] = useState([])
  const [selectedMessageId, setSelectedMessageId] = useState(null)
  const [selectedProvider, setSelectedProvider] = useState('')
  const [selectingMessageId, setSelectingMessageId] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [comparisonError, setComparisonError] = useState('')
  const [selectionError, setSelectionError] = useState('')
  const [retryingProvider, setRetryingProvider] = useState('')
  const [conversationMessages, setConversationMessages] = useState([])

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

  async function refreshConversationDetail(id) {
    if (!id) {
      setConversationMessages([])
      return
    }

    try {
      const conversation = await getConversation(id)
      setConversationMessages(conversation.messages)
    } catch (requestError) {
      // Dallanma şeridi güncel veriyi gösteremeyebilir; ana akışı etkilemez.
      console.error('Konuşma detayı tazelenemedi:', requestError)
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
      setUserMessageId(latestUserMessage?.id ?? null)
      setSubmittedMessage(latestUserMessage?.content ?? '')
      setResponses(latestResponses)
      setSelectedMessageId(conversation.activeMessageId)
      setSelectedProvider(activeMessage?.provider ?? '')
      setConversationMessages(conversation.messages)
    } catch (requestError) {
      setHistoryError(requestError.message)
    } finally {
      setIsOpeningConversation(false)
    }
  }

  function handleNewConversation() {
    setConversationId(null)
    setUserMessageId(null)
    setSubmittedMessage('')
    setResponses([])
    setSelectedMessageId(null)
    setSelectedProvider('')
    setComparisonError('')
    setSelectionError('')
    setHistoryError('')
    setConversationMessages([])
  }

  async function handleSend(message) {
    setSubmittedMessage(message)
    setResponses(
      PROVIDERS.map((provider) => ({
        provider,
        content: '',
        messageId: null,
        error: null,
        streaming: true,
      })),
    )
    setSelectedProvider('')
    setUserMessageId(null)
    setComparisonError('')
    setSelectionError('')
    setIsLoading(true)

    let resolvedConversationId = conversationId

    try {
      await streamCompareMessage(message, conversationId, {
        start: (payload) => {
          resolvedConversationId = payload.conversationId
          setConversationId(payload.conversationId)
          setUserMessageId(payload.userMessageId)
        },
        token: ({ provider, delta }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, content: response.content + delta }
                : response,
            ),
          )
        },
        done: ({ provider, messageId, content }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, messageId, content, streaming: false }
                : response,
            ),
          )
        },
        error: ({ provider, message: errorMessage }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, error: errorMessage, streaming: false }
                : response,
            ),
          )
        },
      })

      await refreshConversations()
      await refreshConversationDetail(resolvedConversationId)
    } catch (requestError) {
      setComparisonError(requestError.message)
      setResponses((current) =>
        current.map((response) => ({ ...response, streaming: false })),
      )
    } finally {
      setIsLoading(false)
    }
  }

  async function handleRetry(provider) {
    if (!submittedMessage) {
      return
    }

    if (!conversationId || !userMessageId) {
      await handleSend(submittedMessage)
      return
    }

    setRetryingProvider(provider)
    setComparisonError('')

    try {
      const retriedResponse = await retryProvider(
        conversationId,
        userMessageId,
        provider,
      )

      setResponses((currentResponses) =>
        currentResponses.map((response) =>
          response.provider === provider ? retriedResponse : response,
        ),
      )

      if (!retriedResponse.error) {
        await refreshConversations()
        await refreshConversationDetail(conversationId)
      }
    } catch (requestError) {
      setResponses((currentResponses) =>
        currentResponses.map((response) =>
          response.provider === provider
            ? { ...response, error: requestError.message }
            : response,
        ),
      )
    } finally {
      setRetryingProvider('')
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
      await refreshConversationDetail(conversationId)
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
    retryingProvider !== '' ||
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

            <div className="app__features" aria-label="Uygulama özellikleri">
              <span>3 yapay zekâ</span>
              <span>Ortak bağlam</span>
              <span>Güvenli API</span>
            </div>
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

        <BranchTreePanel
          messages={conversationMessages}
          activeMessageId={selectedMessageId}
        />

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
                isRetrying={retryingProvider === provider}
                isSelected={
                  selectedMessageId === providerResponse?.messageId
                }
                isSelecting={
                  selectingMessageId === providerResponse?.messageId
                }
                onSelect={handleSelect}
                onRetry={() => handleRetry(provider)}
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
