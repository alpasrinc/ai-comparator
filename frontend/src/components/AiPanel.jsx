const PROVIDER_LABELS = {
  OPENAI: 'ChatGPT',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
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
  const content = response?.content ?? ''
  const panelError = response?.error ?? error

  return (
    <article
      className={`ai-panel ai-panel--${provider.toLowerCase()} ${
        isSelected ? 'ai-panel--selected' : ''
      }`}
    >
      <header className="ai-panel__header">
        <div>
          <span className="ai-panel__provider-code">{provider}</span>
          <h2>{providerLabel}</h2>
        </div>

        <span className="ai-panel__status">
          {isLoading || isRetrying
            ? 'Düşünüyor...'
            : isSelected
              ? 'Seçildi'
              : panelError
                ? 'Hata'
              : content
                ? 'Tamamlandı'
                : 'Hazır'}
        </span>
      </header>

      <div className="ai-panel__content">
        {(isLoading || isRetrying) && (
          <p className="ai-panel__placeholder">
            Yapay zekâ yanıtı bekleniyor...
          </p>
        )}

        {!isLoading && !isRetrying && panelError && (
          <div className="ai-panel__error-block" role="alert">
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

        {!isLoading && !isRetrying && !panelError && content && (
          <p className="ai-panel__response">{content}</p>
        )}

        {!isLoading && !isRetrying && !panelError && !content && (
          <p className="ai-panel__placeholder">
            Bir mesaj gönderdiğinizde cevap burada gösterilecek.
          </p>
        )}
      </div>

      {!isLoading && !isRetrying && !panelError && response && (
        <footer className="ai-panel__footer">
          <button
            type="button"
            className="ai-panel__select-button"
            onClick={() => onSelect(response)}
            disabled={isSelecting || isSelected}
          >
            {isSelecting
              ? 'Seçiliyor...'
              : isSelected
                ? 'Bu cevap seçildi'
                : 'Bu cevapla devam et'}
          </button>
        </footer>
      )}
    </article>
  )
}

export default AiPanel
