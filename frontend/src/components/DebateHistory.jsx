function DebateHistory({ debates, isLoading, activeDebateId, onSelect }) {
  return (
    <aside className="debate-history">
      <h2 className="debate-history__title">Münazaralar</h2>
      {isLoading && <p className="debate-history__empty">Yükleniyor…</p>}
      {!isLoading && debates.length === 0 && (
        <p className="debate-history__empty">Henüz münazara yok.</p>
      )}
      <ul className="debate-history__list">
        {debates.map((debate) => (
          <li key={debate.id}>
            <button
              type="button"
              className={`debate-history__item ${
                debate.id === activeDebateId
                  ? 'debate-history__item--active'
                  : ''
              }`}
              onClick={() => onSelect(debate.id)}
            >
              <span className="debate-history__topic">{debate.topic}</span>
              <span className="debate-history__meta">
                {debate.rounds} tur · {debate.status}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </aside>
  )
}

export default DebateHistory
