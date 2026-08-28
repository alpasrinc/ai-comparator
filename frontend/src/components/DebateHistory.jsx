function formatDate(dateValue) {
  return new Intl.DateTimeFormat('tr-TR', {
    day: '2-digit',
    month: 'short',
  }).format(new Date(dateValue))
}

function DebateHistory({
  debates,
  isLoading,
  activeDebateId,
  onSelect,
  onNewDebate,
  onDelete,
  deletingDebateId,
  isRunning,
  collapsed,
  onToggleCollapse,
}) {
  return (
    <aside
      className={`conversation-sidebar conversation-sidebar--debate${
        collapsed ? ' conversation-sidebar--collapsed' : ''
      }`}
    >
      <div className="conversation-sidebar__header">
        <div className="conversation-sidebar__brand">
          <span className="conversation-sidebar__logo" aria-hidden="true">
            AI
          </span>
          <div>
            <span>AI DISCUSSION</span>
            <h2>Münazaralar</h2>
          </div>
        </div>

        <div className="conversation-sidebar__actions">
          <button
            type="button"
            className="new-conversation-button"
            onClick={onNewDebate}
            disabled={isRunning}
          >
            + Yeni
          </button>
          <button
            type="button"
            className="sidebar-collapse-button"
            onClick={onToggleCollapse}
            aria-label={collapsed ? 'Paneli genişlet' : 'Paneli daralt'}
            title={collapsed ? 'Paneli genişlet' : 'Paneli daralt'}
          >
            {collapsed ? '»' : '«'}
          </button>
        </div>
      </div>

      <nav className="conversation-list" aria-label="Kayıtlı münazaralar">
        {isLoading && (
          <p className="conversation-list__message">Münazaralar yükleniyor...</p>
        )}

        {!isLoading && debates.length === 0 && (
          <p className="conversation-list__message">Henüz münazara yok.</p>
        )}

        {!isLoading &&
          debates.map((debate) => (
            <div className="conversation-list__row" key={debate.id}>
              <button
                type="button"
                className={`conversation-item ${
                  debate.id === activeDebateId
                    ? 'conversation-item--active'
                    : ''
                }`}
                onClick={() => onSelect(debate.id)}
                title={debate.topic}
              >
                <span className="conversation-item__title conversation-item__title--wrap">
                  {debate.topic}
                </span>
                <span className="conversation-item__date">
                  {debate.rounds} tur · {formatDate(debate.updatedAt)}
                </span>
              </button>

              <button
                type="button"
                className="history-delete-button"
                onClick={() => onDelete(debate)}
                disabled={
                  deletingDebateId === debate.id ||
                  (isRunning && activeDebateId === debate.id)
                }
                aria-label={`${debate.topic} münazarasını sil`}
                title={
                  isRunning && activeDebateId === debate.id
                    ? 'Çalışan münazara silinemez'
                    : 'Münazarayı sil'
                }
              >
                {deletingDebateId === debate.id ? '…' : 'Sil'}
              </button>
            </div>
          ))}
      </nav>
    </aside>
  )
}

export default DebateHistory
