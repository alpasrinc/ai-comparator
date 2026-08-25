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
  isSelected,
  isSelecting,
  onSelect,
}) {
  const providerLabel = PROVIDER_LABELS[provider] ?? provider
  const content = response?.content ?? ''

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
          {isLoading
            ? 'Düşünüyor...'
            : isSelected
              ? 'Seçildi'
              : content
                ? 'Tamamlandı'
                : 'Hazır'}
        </span>
      </header>

      <div className="ai-panel__content">
        {isLoading && (
          <p className="ai-panel__placeholder">
            Yapay zekâ yanıtı bekleniyor...
          </p>
        )}

        {!isLoading && error && (
          <p className="ai-panel__error">{error}</p>
        )}

        {!isLoading && !error && content && (
          <p className="ai-panel__response">{content}</p>
        )}

        {!isLoading && !error && !content && (
          <p className="ai-panel__placeholder">
            Bir mesaj gönderdiğinizde cevap burada gösterilecek.
          </p>
        )}
      </div>

      {!isLoading && !error && response && (
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