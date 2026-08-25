import { useState } from 'react'
import BranchTree from './BranchTree'
import ConversationHistory from './ConversationHistory'
import { buildConversationTree } from '../utils/conversationTree'

function BranchTreePanel({ messages, activeMessageId }) {
  const [isOpen, setIsOpen] = useState(false)
  const [previewMessage, setPreviewMessage] = useState(null)

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
                <span>{previewMessage.provider}</span>
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
