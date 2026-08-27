import { useState } from 'react'
import { PROVIDERS, buildDebateRequest, validateDebateForm } from '../utils/debate'

function DebateLauncher({ onStart, disabled }) {
  const [topic, setTopic] = useState('')
  const [participants, setParticipants] = useState(PROVIDERS)
  const [rounds, setRounds] = useState(2)
  const [synthesizer, setSynthesizer] = useState('OPENAI')
  const [error, setError] = useState('')

  function toggleParticipant(provider) {
    setParticipants((current) =>
      current.includes(provider)
        ? current.filter((item) => item !== provider)
        : [...current, provider],
    )
  }

  function handleSubmit(event) {
    event.preventDefault()

    const validationError = validateDebateForm(topic, participants)
    if (validationError) {
      setError(validationError)
      return
    }

    setError('')
    onStart(
      buildDebateRequest({ topic, participants, rounds, synthesizer }),
    )
  }

  return (
    <form className="debate-launcher" onSubmit={handleSubmit}>
      <label className="debate-launcher__field">
        <span>Konu</span>
        <textarea
          value={topic}
          onChange={(event) => setTopic(event.target.value)}
          rows={3}
          placeholder="Münazara konusunu yazın…"
        />
      </label>

      <fieldset className="debate-launcher__field">
        <legend>Katılımcılar</legend>
        {PROVIDERS.map((provider) => (
          <label key={provider} className="debate-launcher__check">
            <input
              type="checkbox"
              aria-label={provider}
              checked={participants.includes(provider)}
              onChange={() => toggleParticipant(provider)}
            />
            {provider}
          </label>
        ))}
      </fieldset>

      <label className="debate-launcher__field">
        <span>Tur sayısı</span>
        <select
          value={rounds}
          onChange={(event) => setRounds(Number(event.target.value))}
        >
          {[1, 2, 3, 4, 5].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className="debate-launcher__field">
        <span>Sentezci</span>
        <select
          value={synthesizer}
          onChange={(event) => setSynthesizer(event.target.value)}
        >
          {PROVIDERS.map((provider) => (
            <option key={provider} value={provider}>
              {provider}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="debate-launcher__error">{error}</p>}

      <button type="submit" disabled={disabled}>
        Münazarayı Başlat
      </button>
    </form>
  )
}

export default DebateLauncher
