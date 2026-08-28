import { useEffect, useState } from 'react'
import DebateHistory from './DebateHistory'
import DebateLauncher from './DebateLauncher'
import DebateTranscript from './DebateTranscript'
import {
  deleteDebate,
  getDebate,
  getDebates,
  startDebateStream,
} from '../services/api'
import { createRoundEntries, normalizeDebateDetail } from '../utils/debate'

function DebateView() {
  const [debates, setDebates] = useState([])
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [activeDebateId, setActiveDebateId] = useState(null)
  const [topic, setTopic] = useState('')
  const [rounds, setRounds] = useState([])
  const [synthesis, setSynthesis] = useState(null)
  const [isRunning, setIsRunning] = useState(false)
  const [error, setError] = useState('')
  const [deletingDebateId, setDeletingDebateId] = useState(null)
  const [historyCollapsed, setHistoryCollapsed] = useState(false)

  useEffect(() => {
    getDebates()
      .then((data) => setDebates(data))
      .catch((requestError) => setError(requestError.message))
      .finally(() => setIsLoadingHistory(false))
  }, [])

  async function refreshHistory() {
    try {
      setDebates(await getDebates())
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  function upsertEntry(roundNumber, provider, updater) {
    setRounds((current) =>
      current.map((round) =>
        round.round === roundNumber
          ? {
              ...round,
              entries: round.entries.map((entry) =>
                entry.provider === provider ? updater(entry) : entry,
              ),
            }
          : round,
      ),
    )
  }

  async function handleStart(request) {
    setError('')
    setActiveDebateId(null)
    setTopic(request.topic)
    setRounds([])
    setSynthesis(null)
    setIsRunning(true)

    try {
      await startDebateStream(request, {
        start: ({ debateId }) => setActiveDebateId(debateId),
        'round-start': ({ round }) => {
          setRounds((current) => [
            ...current,
            { round, entries: createRoundEntries(request.participants) },
          ])
        },
        token: ({ round, provider, delta }) => {
          if (round === 0) {
            setSynthesis((current) => ({
              content: (current?.content ?? '') + delta,
              streaming: true,
              error: null,
            }))
            return
          }
          upsertEntry(round, provider, (entry) => ({
            ...entry,
            content: entry.content + delta,
          }))
        },
        'participant-done': ({ round, provider, content, usage }) => {
          upsertEntry(round, provider, (entry) => ({
            ...entry,
            content,
            usage,
            streaming: false,
          }))
        },
        'participant-error': ({ round, provider, message }) => {
          if (round === 0) {
            setSynthesis({ content: '', streaming: false, error: message })
            return
          }
          upsertEntry(round, provider, (entry) => ({
            ...entry,
            error: message,
            streaming: false,
          }))
        },
        'synthesis-done': ({ content, usage }) => {
          setSynthesis({ content, streaming: false, error: null, usage })
        },
        done: () => {
          setIsRunning(false)
          refreshHistory()
        },
      })
    } catch (requestError) {
      setError(requestError.message)
      setIsRunning(false)
    }
  }

  async function handleOpenDebate(debateId) {
    setError('')
    try {
      const detail = await getDebate(debateId)
      setActiveDebateId(detail.id)
      setTopic(detail.topic)

      const { rounds: rebuiltRounds, synthesis: rebuiltSynthesis } =
        normalizeDebateDetail(detail)
      setRounds(rebuiltRounds)
      setSynthesis(rebuiltSynthesis)
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  async function handleDeleteDebate(debate) {
    const confirmed = window.confirm(
      `“${debate.topic}” münazarası kalıcı olarak silinsin mi?`,
    )

    if (!confirmed) {
      return
    }

    setDeletingDebateId(debate.id)
    setError('')

    try {
      await deleteDebate(debate.id)
      setDebates((current) =>
        current.filter((item) => item.id !== debate.id),
      )

      if (activeDebateId === debate.id) {
        setActiveDebateId(null)
        setTopic('')
        setRounds([])
        setSynthesis(null)
      }
    } catch (requestError) {
      setError(`Münazara silinemedi: ${requestError.message}`)
    } finally {
      setDeletingDebateId(null)
    }
  }

  function handleNewDebate() {
    setActiveDebateId(null)
    setTopic('')
    setRounds([])
    setSynthesis(null)
    setError('')
  }

  const hasContent = rounds.length > 0 || synthesis
  const showLauncher = !activeDebateId && !isRunning

  return (
    <div
      className={`app-layout app-layout--debate${
        historyCollapsed ? ' app-layout--collapsed' : ''
      }`}
    >
      <DebateHistory
        debates={debates}
        isLoading={isLoadingHistory}
        activeDebateId={activeDebateId}
        onSelect={handleOpenDebate}
        onNewDebate={handleNewDebate}
        onDelete={handleDeleteDebate}
        deletingDebateId={deletingDebateId}
        isRunning={isRunning}
        collapsed={historyCollapsed}
        onToggleCollapse={() => setHistoryCollapsed((value) => !value)}
      />
      <main className="app">
        <header className="app__header">
          <div>
            <p className="app__eyebrow">AI COMPARATOR</p>
            <h1>Yapay zekâ münazarası</h1>
            <p className="app__description">
              Bir konu verin; yapay zekâlar tartışsın, tarafsız bir sentezci
              ortak cevabı yazsın.
            </p>
          </div>
        </header>

        {error && (
          <div className="selection-notice selection-notice--error">
            {error}
          </div>
        )}

        {showLauncher && (
          <DebateLauncher onStart={handleStart} disabled={isRunning} />
        )}

        {hasContent && (
          <DebateTranscript
            topic={topic}
            rounds={rounds}
            synthesis={synthesis}
          />
        )}
      </main>
    </div>
  )
}

export default DebateView
