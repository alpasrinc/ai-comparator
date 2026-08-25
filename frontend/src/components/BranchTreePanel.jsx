import { useEffect, useState } from 'react'
import BranchTree from './BranchTree'
import ConversationHistory from './ConversationHistory'
import { buildConversationTree } from '../utils/conversationTree'

const PROVIDER_LABELS = {
  OPENAI: 'ChatGPT',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
}

function BranchTreePanel({ messages, activeMessageId }) {
  const [isOpen, setIsOpen] = useState(false)
  const [previewMessage, setPreviewMessage] = useState(null)

  // Clear preview when conversation changes to avoid stale preview across switches
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPreviewMessage(null)
  }, [messages])

  const turnCount = messages.filter(
    (message) => message.role === 'USER',
  ).length

  if (turnCount === 0) {
    return null
  }

  const { root, activePath } = buildConversationTree(
    messages,
    activeMessageId,
  )

  return (
    <div className="branch-panel">
      <button
        type="button"
        className="branch-panel__toggle"
        onClick={() => setIsOpen((open) => !open)}
        aria-expanded={isOpen}
      >
        <span aria-hidden="true">{isOpen ? '▴' : '▾'}</span>
        Dallanma haritası ({turnCount} tur)
      </button>

      {isOpen && (
        <div className="branch-panel__body">
          <div className="branch-panel__tree">
            <BranchTree
              root={root}
              activePath={activePath}
              onPreview={setPreviewMessage}
            />
          </div>

          {previewMessage && (
            <div className="branch-panel__preview">
              <div className="branch-panel__preview-header">
                <span>{PROVIDER_LABELS[previewMessage.provider] ?? previewMessage.provider}</span>
                <button
                  type="button"
                  onClick={() => setPreviewMessage(null)}
                  aria-label="Kapat"
                >
                  ×
                </button>
              </div>
              <p>{previewMessage.content}</p>
            </div>
          )}

          <div className="branch-panel__history">
            <ConversationHistory
              messages={messages}
              activePath={activePath}
            />
          </div>
        </div>
      )}
    </div>
  )
}

export default BranchTreePanel
