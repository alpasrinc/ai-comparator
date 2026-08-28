const OPTIONS = [
  { value: 'LOW', label: 'Az', hint: 'Kısa' },
  { value: 'MEDIUM', label: 'Orta', hint: 'Dengeli' },
  { value: 'HIGH', label: 'Çok', hint: 'Detaylı' },
]

function IntensitySelector({ value, onChange, disabled }) {
  return (
    <div
      className="intensity-selector"
      role="radiogroup"
      aria-label="Yanıt yoğunluğu"
    >
      <span className="intensity-selector__label">
        <span>Yoğunluk</span>
        <small>Cevap uzunluğunu ayarlar</small>
      </span>
      <div className="intensity-selector__options">
        {OPTIONS.map((option) => (
          <button
            type="button"
            key={option.value}
            className={`intensity-option${
              value === option.value ? ' intensity-option--active' : ''
            }`}
            onClick={() => onChange(option.value)}
            disabled={disabled}
            role="radio"
            aria-checked={value === option.value}
          >
            <strong>{option.label}</strong>
            <small>{option.hint}</small>
          </button>
        ))}
      </div>
    </div>
  )
}

export default IntensitySelector
