import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkBreaks from 'remark-breaks'
import TokenUsageBadge from './TokenUsageBadge'

const PROVIDER_LABELS = {
  OPENAI: 'ChatGPT',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
}

const PROVIDER_MARKS = {
  OPENAI: 'G',
  ANTHROPIC: 'C',
  GEMINI: '◆',
}

function AiPanel({
  provider,
  response,
  isLoading,
  error,
  isRetrying,
  isSelected,
  isSelecting,
  onSelect,
  onRetry,
}) {
  const providerLabel = PROVIDER_LABELS[provider] ?? provider
  const providerMark = PROVIDER_MARKS[provider] ?? 'AI'
  const content = response?.content ?? ''
  const panelError = response?.error ?? error
  const isStreaming = Boolean(response?.streaming)
  const waiting = (isLoading || isRetrying) && !content && !panelError

  let statusLabel = 'Hazır'
  let statusState = 'ready'

  if (waiting) {
    statusLabel = 'Düşünüyor'
    statusState = 'loading'
  } else if (isStreaming && !panelError) {
    statusLabel = 'Yazıyor'
    statusState = 'loading'
  } else if (isSelected) {
    statusLabel = 'Seçildi'
    statusState = 'selected'
  } else if (panelError) {
    statusLabel = 'Hata'
    statusState = 'error'
  } else if (content) {
    statusLabel = 'Tamamlandı'
    statusState = 'success'
  }

  return (
    <article
      className={`ai-panel ai-panel--${provider.toLowerCase()} ${
        isSelected ? 'ai-panel--selected' : ''
      }`}
    >
      <header className="ai-panel__header">
        <div className="ai-panel__identity">
          <span className="ai-panel__avatar" aria-hidden="true">
            {providerMark}
          </span>

          <div>
            <span className="ai-panel__provider-code">{provider}</span>
            <h2>{providerLabel}</h2>
          </div>
        </div>

        <span
          className={`ai-panel__status ai-panel__status--${statusState}`}
        >
          <span className="ai-panel__status-dot" aria-hidden="true" />
          {statusLabel}
        </span>
      </header>

      <div className="ai-panel__content">
        {waiting && (
          <div className="ai-panel__skeleton" aria-label="Yanıt bekleniyor">
            <span />
            <span />
            <span />
            <span />
          </div>
        )}

        {!waiting && panelError && (
          <div className="ai-panel__error-block" role="alert">
            <span className="ai-panel__error-icon" aria-hidden="true">
              !
            </span>
            <p className="ai-panel__error">{panelError}</p>
            <button
              type="button"
              className="ai-panel__retry-button"
              onClick={onRetry}
            >
              Tekrar dene
            </button>
          </div>
        )}

        {!waiting && !panelError && content && (
          <div className="ai-panel__response">
            <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>
              {content}
            </ReactMarkdown>
            {isStreaming && (
              <span className="ai-panel__cursor" aria-hidden="true" />
            )}
          </div>
        )}

        {!waiting && !panelError && content && !isStreaming && (
          <TokenUsageBadge usage={response?.usage} />
        )}

        {!waiting && !panelError && !content && (
          <div className="ai-panel__empty">
            <span aria-hidden="true">✦</span>
            <p>Bir mesaj gönderdiğinizde cevap burada gösterilecek.</p>
          </div>
        )}
      </div>

      {!waiting && !panelError && !isStreaming && response && (
        <footer className="ai-panel__footer">
          <button
            type="button"
            className="ai-panel__select-button"
            onClick={() => onSelect(response)}
            disabled={isSelecting || isSelected || !response.messageId}
          >
            {isSelecting
              ? 'Seçiliyor...'
              : isSelected
                ? 'Bu cevap seçildi'
                : 'Bu cevapla devam et →'}
          </button>
        </footer>
      )}
    </article>
  )
}

export default AiPanel
