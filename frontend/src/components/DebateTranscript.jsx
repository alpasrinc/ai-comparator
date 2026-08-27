const PROVIDER_LABELS = {
  OPENAI: 'OpenAI',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
}

function DebateTranscript({ topic, rounds, synthesis }) {
  return (
    <div className="debate-transcript">
      {topic && (
        <div className="debate-transcript__topic">
          <span>Konu</span>
          <p>{topic}</p>
        </div>
      )}

      {rounds.map((round) => (
        <section key={round.round} className="debate-round">
          <h3 className="debate-round__title">
            Tur {round.round}
            {round.round > 1 ? ' — eleştiri / revizyon' : ''}
          </h3>

          <div className="debate-round__entries">
            {round.entries.map((entry) => (
              <article
                key={entry.provider}
                className={`debate-entry debate-entry--${entry.provider.toLowerCase()}`}
              >
                <header className="debate-entry__provider">
                  {PROVIDER_LABELS[entry.provider] ?? entry.provider}
                  {entry.streaming && (
                    <span className="debate-entry__typing"> yazıyor…</span>
                  )}
                </header>
                {entry.error ? (
                  <p className="debate-entry__error">{entry.error}</p>
                ) : (
                  <p className="debate-entry__content">{entry.content}</p>
                )}
              </article>
            ))}
          </div>
        </section>
      ))}

      {synthesis && (
        <section className="debate-synthesis">
          <h3 className="debate-synthesis__title">⚖️ Ortak Cevap</h3>
          {synthesis.error ? (
            <p className="debate-entry__error">{synthesis.error}</p>
          ) : (
            <p className="debate-synthesis__content">
              {synthesis.content}
              {synthesis.streaming && (
                <span className="debate-entry__typing"> yazıyor…</span>
              )}
            </p>
          )}
        </section>
      )}
    </div>
  )
}

export default DebateTranscript
