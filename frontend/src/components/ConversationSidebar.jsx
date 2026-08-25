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
}) {
  return (
    <aside className="conversation-sidebar">
      <div className="conversation-sidebar__header">
        <div>
          <span>AI COMPARATOR</span>
          <h2>Konuşmalar</h2>
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
            <button
              key={conversation.id}
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
          ))}
      </nav>
    </aside>
  )
}

export default ConversationSidebar