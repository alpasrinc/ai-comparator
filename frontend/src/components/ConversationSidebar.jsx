function formatDate(dateValue) {
  return new Intl.DateTimeFormat('tr-TR', {
    day: '2-digit',
    month: 'short',
  }).format(new Date(dateValue))
}

function ConversationSidebar({
  conversations,
  activeConversationId,
  isLoading,
  onSelect,
  onNewConversation,
  onDelete,
  deletingConversationId,
  isBusy,
}) {
  return (
    <aside className="conversation-sidebar">
      <div className="conversation-sidebar__header">
        <div className="conversation-sidebar__brand">
          <span className="conversation-sidebar__logo" aria-hidden="true">
            AI
          </span>
          <div>
            <span>AI COMPARATOR</span>
            <h2>Konuşmalar</h2>
          </div>
        </div>

        <button
          type="button"
          className="new-conversation-button"
          onClick={onNewConversation}
        >
          + Yeni
        </button>
      </div>

      <nav
        className="conversation-list"
        aria-label="Kayıtlı konuşmalar"
      >
        {isLoading && (
          <p className="conversation-list__message">
            Konuşmalar yükleniyor...
          </p>
        )}

        {!isLoading && conversations.length === 0 && (
          <p className="conversation-list__message">
            Henüz kayıtlı konuşma yok.
          </p>
        )}

        {!isLoading &&
          conversations.map((conversation) => (
            <div className="conversation-list__row" key={conversation.id}>
              <button
                type="button"
                className={`conversation-item ${
                  activeConversationId === conversation.id
                    ? 'conversation-item--active'
                    : ''
                }`}
                onClick={() => onSelect(conversation.id)}
                title={conversation.title}
              >
                <span className="conversation-item__title">
                  {conversation.title}
                </span>

                <span className="conversation-item__date">
                  {formatDate(conversation.updatedAt)}
                </span>
              </button>

              <button
                type="button"
                className="history-delete-button"
                onClick={() => onDelete(conversation)}
                disabled={
                  deletingConversationId === conversation.id ||
                  (isBusy && activeConversationId === conversation.id)
                }
                aria-label={`${conversation.title} konuşmasını sil`}
                title={
                  isBusy && activeConversationId === conversation.id
                    ? 'Yanıt üretilirken konuşma silinemez'
                    : 'Konuşmayı sil'
                }
              >
                {deletingConversationId === conversation.id ? '…' : 'Sil'}
              </button>
            </div>
          ))}
      </nav>
    </aside>
  )
}

export default ConversationSidebar
