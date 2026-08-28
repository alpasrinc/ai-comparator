const PROVIDER_DETAILS = {
  OPENAI: { name: 'ChatGPT', mark: 'G', tone: 'green' },
  ANTHROPIC: { name: 'Claude', mark: 'C', tone: 'orange' },
  GEMINI: { name: 'Gemini', mark: '◆', tone: 'blue' },
}

function CompareProviderSelector({ providers, selected, onToggle, disabled }) {
  return (
    <fieldset className="compare-provider-selector" disabled={disabled}>
      <legend>
        <span>Yanıt verecek yapay zekâlar</span>
        <small>Bir veya daha fazla model seçin</small>
      </legend>

      <div className="compare-provider-selector__grid">
        {providers.map((provider) => {
          const details = PROVIDER_DETAILS[provider]
          const isSelected = selected.includes(provider)
          const isLastSelected = isSelected && selected.length === 1

          return (
            <label
              key={provider}
              className={`compare-provider-option compare-provider-option--${details.tone}${
                isSelected ? ' compare-provider-option--selected' : ''
              }`}
            >
              <input
                type="checkbox"
                checked={isSelected}
                disabled={disabled || isLastSelected}
                onChange={() => onToggle(provider)}
              />
              <span className="compare-provider-option__mark" aria-hidden="true">
                {details.mark}
              </span>
              <span className="compare-provider-option__text">
                <strong>{details.name}</strong>
                <small>{provider}</small>
              </span>
              <span className="compare-provider-option__check" aria-hidden="true">
                ✓
              </span>
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}

export default CompareProviderSelector
