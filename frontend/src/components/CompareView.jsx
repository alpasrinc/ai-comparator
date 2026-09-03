import { useEffect, useState } from 'react'
import AiPanel from './AiPanel'
import BranchTreePanel from './BranchTreePanel'
import ChatInput from './ChatInput'
import CompareProviderSelector from './CompareProviderSelector'
import ConversationSidebar from './ConversationSidebar'
import DocumentUploader from './DocumentUploader'
import IntensitySelector from './IntensitySelector'
import SourceList from './SourceList'
import {
  deleteConversation,
  deleteDocument,
  getConversation,
  getConversations,
  listDocuments,
  retryProvider,
  selectActiveMessage,
  streamCompareMessage,
  uploadDocument,
} from '../services/api'

const PROVIDERS = ['OPENAI', 'ANTHROPIC', 'GEMINI']

function CompareView({ backendStatus, backendError }) {
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
  const [deletingConversationId, setDeletingConversationId] = useState(null)
  const [selectedProviders, setSelectedProviders] = useState(PROVIDERS)
  const [intensity, setIntensity] = useState('MEDIUM')
  const [historyCollapsed, setHistoryCollapsed] = useState(false)
  const [documents, setDocuments] = useState([])
  const [sources, setSources] = useState([])
  const [sourcesUnavailable, setSourcesUnavailable] = useState(false)

  useEffect(() => {
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

  useEffect(() => {
    if (!conversationId) {
      setDocuments([])
      return
    }

    let cancelled = false

    listDocuments(conversationId)
      .then((loaded) => {
        if (!cancelled) {
          setDocuments(loaded)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setDocuments([])
        }
      })

    return () => {
      cancelled = true
    }
  }, [conversationId])

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

  async function handleUploadDocument(file) {
    const uploaded = await uploadDocument(conversationId, file)
    setDocuments((current) => [...current, uploaded])
  }

  async function handleDeleteDocument(documentId) {
    await deleteDocument(conversationId, documentId)
    setDocuments((current) =>
      current.filter((document) => document.id !== documentId),
    )
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
              usage: message.usage,
            }))
        : []

      const activeMessage = conversation.messages.find(
        (message) => message.id === conversation.activeMessageId,
      )

      setConversationId(conversation.id)
      setUserMessageId(latestUserMessage?.id ?? null)
      setSubmittedMessage(latestUserMessage?.content ?? '')
      setResponses(latestResponses)
      setSelectedProviders(
        latestResponses.length > 0
          ? latestResponses.map((response) => response.provider)
          : PROVIDERS,
      )
      setSelectedMessageId(conversation.activeMessageId)
      setSelectedProvider(activeMessage?.provider ?? '')
      setConversationMessages(conversation.messages)
      setSources(latestUserMessage?.sources ?? [])
      setSourcesUnavailable(false)
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
    setSelectedProviders(PROVIDERS)
    setSources([])
    setSourcesUnavailable(false)
  }

  function handleToggleProvider(provider) {
    setSelectedProviders((current) => {
      if (current.includes(provider)) {
        return current.length === 1
          ? current
          : current.filter((item) => item !== provider)
      }

      return PROVIDERS.filter(
        (item) => current.includes(item) || item === provider,
      )
    })
  }

  async function handleDeleteConversation(conversation) {
    const confirmed = window.confirm(
      `“${conversation.title}” konuşması kalıcı olarak silinsin mi?`,
    )

    if (!confirmed) {
      return
    }

    setDeletingConversationId(conversation.id)
    setHistoryError('')

    try {
      await deleteConversation(conversation.id)
      setConversations((current) =>
        current.filter((item) => item.id !== conversation.id),
      )

      if (conversationId === conversation.id) {
        handleNewConversation()
      }
    } catch (requestError) {
      setHistoryError(`Konuşma silinemedi: ${requestError.message}`)
    } finally {
      setDeletingConversationId(null)
    }
  }

  async function handleSend(message) {
    setSubmittedMessage(message)
    setResponses(
      selectedProviders.map((provider) => ({
        provider,
        content: '',
        messageId: null,
        error: null,
        streaming: true,
      })),
    )
    setSelectedProvider('')
    setUserMessageId(null)
    setSources([])
    setSourcesUnavailable(false)
    setComparisonError('')
    setSelectionError('')
    setIsLoading(true)

    let resolvedConversationId = conversationId
    let completedSingleResponse = null

    try {
      await streamCompareMessage(
        message,
        conversationId,
        selectedProviders,
        intensity,
        {
        start: (payload) => {
          resolvedConversationId = payload.conversationId
          setConversationId(payload.conversationId)
          setUserMessageId(payload.userMessageId)
          setSources(payload.sources ?? [])
          setSourcesUnavailable(payload.sourcesUnavailable ?? false)
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
        done: ({ provider, messageId, content, usage }) => {
          if (selectedProviders.length === 1) {
            completedSingleResponse = { provider, messageId, content }
          }

          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, messageId, content, usage, streaming: false }
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
        },
      )

      if (completedSingleResponse?.messageId) {
        try {
          const result = await selectActiveMessage(
            resolvedConversationId,
            completedSingleResponse.messageId,
          )
          setSelectedMessageId(result.activeMessageId)
          setSelectedProvider(result.provider)
        } catch (requestError) {
          setSelectionError(requestError.message)
        }
      }

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
        if (selectedProviders.length === 1 && retriedResponse.messageId) {
          const result = await selectActiveMessage(
            conversationId,
            retriedResponse.messageId,
          )
          setSelectedMessageId(result.activeMessageId)
          setSelectedProvider(result.provider)
        }

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

  const selectedCurrentResponse =
    selectedMessageId != null &&
    responses.some(
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
    <div
      className={`app-layout${
        historyCollapsed ? ' app-layout--collapsed' : ''
      }`}
    >
      <ConversationSidebar
        conversations={conversations}
        activeConversationId={conversationId}
        isLoading={isLoadingConversations}
        onSelect={handleOpenConversation}
        onNewConversation={handleNewConversation}
        onDelete={handleDeleteConversation}
        deletingConversationId={deletingConversationId}
        isBusy={isLoading}
        collapsed={historyCollapsed}
        onToggleCollapse={() => setHistoryCollapsed((value) => !value)}
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
            {historyError}
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

        <div className="compare-controls">
          <CompareProviderSelector
            providers={PROVIDERS}
            selected={selectedProviders}
            onToggle={handleToggleProvider}
            disabled={
              isLoading ||
              isOpeningConversation ||
              selectingMessageId !== null ||
              retryingProvider !== ''
            }
          />

          <IntensitySelector
            value={intensity}
            onChange={setIntensity}
            disabled={
              isLoading ||
              isOpeningConversation ||
              selectingMessageId !== null ||
              retryingProvider !== ''
            }
          />
        </div>

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

        <SourceList sources={sources} unavailable={sourcesUnavailable} />

        <section
          className={`ai-grid ai-grid--${selectedProviders.length}`}
          aria-label="Yapay zekâ cevapları"
        >
          {selectedProviders.map((provider) => {
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
          providerCount={selectedProviders.length}
        />

        <DocumentUploader
          documents={documents}
          disabled={conversationId === null}
          onUpload={handleUploadDocument}
          onDelete={handleDeleteDocument}
        />
      </main>
    </div>
  )
}

export default CompareView
