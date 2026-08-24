const PROVIDER_LABELS = {
  OPENAI: 'ChatGPT',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
}

function AiPanel({ provider, response, isLoading, error }) {
  const providerLabel = PROVIDER_LABELS[provider] ?? provider

  return (
    <article className={`ai-panel ai-panel--${provider.toLowerCase()}`}>
      <header className="ai-panel__header">
        <div>
          <span className="ai-panel__provider-code">{provider}</span>
          <h2>{providerLabel}</h2>
        </div>

        <span className="ai-panel__status">
          {isLoading ? 'Düşünüyor...' : response ? 'Tamamlandı' : 'Hazır'}
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

        {!isLoading && !error && response && (
          <p className="ai-panel__response">{response}</p>
        )}

        {!isLoading && !error && !response && (
          <p className="ai-panel__placeholder">
            Bir mesaj gönderdiğinizde cevap burada gösterilecek.
          </p>
        )}
      </div>
    </article>
  )
}

export default AiPanel