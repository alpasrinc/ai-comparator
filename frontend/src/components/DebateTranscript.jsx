import { useEffect, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkBreaks from 'remark-breaks'
import remarkGfm from 'remark-gfm'
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

const PROVIDER_ORDER = ['OPENAI', 'ANTHROPIC', 'GEMINI']
const AUTO_SCROLL_THRESHOLD = 80

function isNearBottom(element) {
  return (
    element.scrollHeight - element.scrollTop - element.clientHeight <=
    AUTO_SCROLL_THRESHOLD
  )
}

function orderedProviders(rounds) {
  const present = new Set()
  rounds.forEach((round) =>
    round.entries.forEach((entry) => present.add(entry.provider)),
  )

  const ordered = PROVIDER_ORDER.filter((provider) => present.has(provider))
  const extras = [...present].filter(
    (provider) => !PROVIDER_ORDER.includes(provider),
  )

  return [...ordered, ...extras]
}

function columnStatus(turns) {
  if (turns.some((turn) => turn.entry.streaming)) {
    return { className: 'debate-entry__status--streaming', label: 'Yazıyor…' }
  }
  if (turns.some((turn) => turn.entry.error)) {
    return { className: 'debate-entry__status--error', label: 'Hata' }
  }
  return { className: 'debate-entry__status--done', label: 'Tamamlandı' }
}

function DebateTranscript({ topic, rounds, synthesis }) {
  const providers = orderedProviders(rounds)
  const columnBodyRefs = useRef(new Map())
  const columnAutoScroll = useRef(new Map())
  const synthesisBodyRef = useRef(null)
  const synthesisAutoScroll = useRef(true)
  const transcriptEndRef = useRef(null)
  const pageAutoScroll = useRef(true)

  const streamingSignature = rounds
    .flatMap((round) =>
      round.entries.map(
        (entry) =>
          `${round.round}:${entry.provider}:${entry.content.length}:${entry.streaming}`,
      ),
    )
    .concat(
      synthesis
        ? [`synthesis:${synthesis.content.length}:${synthesis.streaming}`]
        : [],
    )
    .join('|')

  useEffect(() => {
    let previousScrollY = window.scrollY

    function handlePageScroll() {
      const currentScrollY = window.scrollY
      const distanceFromBottom =
        document.documentElement.scrollHeight -
        currentScrollY -
        window.innerHeight

      if (currentScrollY < previousScrollY - 2) {
        pageAutoScroll.current = false
      } else if (distanceFromBottom <= AUTO_SCROLL_THRESHOLD) {
        pageAutoScroll.current = true
      }

      previousScrollY = currentScrollY
    }

    window.addEventListener('scroll', handlePageScroll, { passive: true })
    return () => window.removeEventListener('scroll', handlePageScroll)
  }, [])

  useEffect(() => {
    const frameId = window.requestAnimationFrame(() => {
      providers.forEach((provider) => {
        const body = columnBodyRefs.current.get(provider)
        if (body && columnAutoScroll.current.get(provider) !== false) {
          body.scrollTop = body.scrollHeight
        }
      })

      if (synthesisBodyRef.current && synthesisAutoScroll.current) {
        synthesisBodyRef.current.scrollTop =
          synthesisBodyRef.current.scrollHeight
      }

      const isStreaming =
        rounds.some((round) =>
          round.entries.some((entry) => entry.streaming),
        ) || synthesis?.streaming

      if (isStreaming && pageAutoScroll.current) {
        transcriptEndRef.current?.scrollIntoView({
          block: 'end',
          behavior: 'smooth',
        })
      }
    })

    return () => window.cancelAnimationFrame(frameId)
  }, [providers, rounds, streamingSignature, synthesis])

  return (
    <div className="debate-transcript">
      {topic && (
        <div className="debate-transcript__topic">
          <span>Konu</span>
          <p>{topic}</p>
        </div>
      )}

      {providers.length > 0 && (
        <div className={`debate-columns debate-columns--${providers.length}`}>
          {providers.map((provider) => {
            const turns = rounds
              .map((round) => ({
                round: round.round,
                entry: round.entries.find(
                  (entry) => entry.provider === provider,
                ),
              }))
              .filter((turn) => turn.entry)

            const status = columnStatus(turns)

            return (
              <article
                key={provider}
                className={`debate-column debate-column--${provider.toLowerCase()}`}
              >
                <header className="debate-entry__header">
                  <span className="debate-entry__mark" aria-hidden="true">
                    {PROVIDER_MARKS[provider] ?? 'AI'}
                  </span>
                  <span className="debate-entry__identity">
                    <strong>{PROVIDER_LABELS[provider] ?? provider}</strong>
                    <small>{provider}</small>
                  </span>
                  <span className={`debate-entry__status ${status.className}`}>
                    {status.label}
                  </span>
                </header>

                <div
                  className="debate-column__body"
                  ref={(element) => {
                    if (element) {
                      columnBodyRefs.current.set(provider, element)
                    } else {
                      columnBodyRefs.current.delete(provider)
                    }
                  }}
                  onScroll={(event) => {
                    columnAutoScroll.current.set(
                      provider,
                      isNearBottom(event.currentTarget),
                    )
                  }}
                >
                  {turns.map(({ round, entry }) => (
                    <div key={round} className="debate-turn">
                      <span className="debate-turn__label">
                        Tur {round}
                        {round > 1 ? ' — eleştiri / revizyon' : ''}
                      </span>
                      {entry.error ? (
                        <p className="debate-entry__error">{entry.error}</p>
                      ) : entry.content ? (
                        <>
                          <div className="debate-entry__content">
                            <ReactMarkdown
                              remarkPlugins={[remarkGfm, remarkBreaks]}
                            >
                              {entry.content}
                            </ReactMarkdown>
                          </div>
                          {!entry.streaming && (
                            <TokenUsageBadge usage={entry.usage} />
                          )}
                        </>
                      ) : (
                        <div className="debate-entry__waiting">
                          <span />
                          <span />
                          <span />
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </article>
            )
          })}
        </div>
      )}

      {synthesis && (
        <section className="debate-synthesis">
          <header className="debate-synthesis__header">
            <div>
              <span className="debate-synthesis__eyebrow">SONUÇ</span>
              <h3 className="debate-synthesis__title">⚖️ Ortak Cevap</h3>
            </div>
            <span className="debate-entry__status">
              {synthesis.streaming ? 'Yazılıyor…' : 'Tamamlandı'}
            </span>
          </header>
          <div
            className="debate-synthesis__body"
            ref={synthesisBodyRef}
            onScroll={(event) => {
              synthesisAutoScroll.current = isNearBottom(event.currentTarget)
            }}
          >
            {synthesis.error ? (
              <p className="debate-entry__error">{synthesis.error}</p>
            ) : (
              <div className="debate-synthesis__content">
                <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>
                  {synthesis.content}
                </ReactMarkdown>
                {!synthesis.streaming && (
                  <TokenUsageBadge usage={synthesis.usage} />
                )}
              </div>
            )}
          </div>
        </section>
      )}
      <div ref={transcriptEndRef} aria-hidden="true" />
    </div>
  )
}

export default DebateTranscript
