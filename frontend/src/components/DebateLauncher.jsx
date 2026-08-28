import { useState } from 'react'
import IntensitySelector from './IntensitySelector'
import { PROVIDERS, buildDebateRequest, validateDebateForm } from '../utils/debate'

const PROVIDER_DETAILS = {
  OPENAI: { name: 'ChatGPT', initial: 'G', tone: 'green' },
  ANTHROPIC: { name: 'Claude', initial: 'C', tone: 'orange' },
  GEMINI: { name: 'Gemini', initial: '◆', tone: 'blue' },
}

function DebateLauncher({ onStart, disabled }) {
  const [topic, setTopic] = useState('')
  const [participants, setParticipants] = useState(PROVIDERS)
  const [rounds, setRounds] = useState(2)
  const [synthesizer, setSynthesizer] = useState('OPENAI')
  const [intensity, setIntensity] = useState('MEDIUM')
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
      buildDebateRequest({
        topic,
        participants,
        rounds,
        synthesizer,
        intensity,
      }),
    )
  }

  return (
    <form className="debate-launcher" onSubmit={handleSubmit}>
      <div className="debate-launcher__intro">
        <div className="debate-launcher__intro-icon" aria-hidden="true">
          ✦
        </div>
        <div>
          <h2>Münazara ayarları</h2>
          <p>Konuyu belirleyin, tartışacak modelleri ve sentezciyi seçin.</p>
        </div>
      </div>

      <label className="debate-launcher__field">
        <span className="debate-launcher__label">
          <span>Konu</span>
          <small>{topic.length} karakter</small>
        </span>
        <textarea
          value={topic}
          onChange={(event) => setTopic(event.target.value)}
          rows={4}
          placeholder="Münazara konusunu yazın…"
        />
      </label>

      <fieldset className="debate-launcher__participants">
        <legend>
          Katılımcılar
          <small>En az iki model seçin</small>
        </legend>
        <div className="debate-launcher__provider-grid">
          {PROVIDERS.map((provider) => {
            const details = PROVIDER_DETAILS[provider]
            const isSelected = participants.includes(provider)

            return (
              <label
                key={provider}
                className={`debate-launcher__provider debate-launcher__provider--${details.tone}${isSelected ? ' debate-launcher__provider--selected' : ''}`}
              >
                <input
                  type="checkbox"
                  aria-label={provider}
                  checked={isSelected}
                  onChange={() => toggleParticipant(provider)}
                />
                <span className="debate-launcher__provider-mark" aria-hidden="true">
                  {details.initial}
                </span>
                <span className="debate-launcher__provider-text">
                  <strong>{details.name}</strong>
                  <small>{provider}</small>
                </span>
                <span className="debate-launcher__provider-check" aria-hidden="true">
                  ✓
                </span>
              </label>
            )
          })}
        </div>
      </fieldset>

      <div className="debate-launcher__settings">
        <label className="debate-launcher__field">
          <span className="debate-launcher__label">Tur sayısı</span>
          <span className="debate-launcher__select-wrap">
            <select
              value={rounds}
              onChange={(event) => setRounds(Number(event.target.value))}
            >
              {[1, 2, 3, 4, 5].map((value) => (
                <option key={value} value={value}>
                  {value} {value === 1 ? 'tur' : 'tur'}
                </option>
              ))}
            </select>
          </span>
        </label>

        <label className="debate-launcher__field">
          <span className="debate-launcher__label">Sentezci</span>
          <span className="debate-launcher__select-wrap">
            <select
              value={synthesizer}
              onChange={(event) => setSynthesizer(event.target.value)}
            >
              {PROVIDERS.map((provider) => (
                <option key={provider} value={provider}>
                  {PROVIDER_DETAILS[provider].name}
                </option>
              ))}
            </select>
          </span>
        </label>
      </div>

      <div className="debate-launcher__intensity">
        <IntensitySelector value={intensity} onChange={setIntensity} />
      </div>

      {error && <p className="debate-launcher__error">{error}</p>}

      <button className="debate-launcher__submit" type="submit" disabled={disabled}>
        <span>Münazarayı Başlat</span>
        <span className="debate-launcher__submit-icon" aria-hidden="true">→</span>
      </button>
    </form>
  )
}

export default DebateLauncher
