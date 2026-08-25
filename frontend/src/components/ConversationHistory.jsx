import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkBreaks from 'remark-breaks'

const PROVIDER_LABELS = {
  OPENAI: 'ChatGPT',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
}

function ConversationHistory({ messages, activePath }) {
  const activeMessages = messages
    .filter((message) => activePath.has(message.id))
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))

  if (activeMessages.length === 0) {
    return (
      <p className="conversation-history__empty">
        Henüz bir dal seçilmedi.
      </p>
    )
  }

  return (
    <div className="conversation-history">
      {activeMessages.map((message) => (
        <div key={message.id} className="conversation-history__turn">
          {message.role === 'USER' ? (
            <p className="conversation-history__user">{message.content}</p>
          ) : (
            <div className="conversation-history__assistant">
              <span className="conversation-history__provider">
                {PROVIDER_LABELS[message.provider] ?? message.provider}
              </span>
              <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>
                {message.content}
              </ReactMarkdown>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

export default ConversationHistory
