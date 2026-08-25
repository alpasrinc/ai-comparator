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
  const [isTreeOpen, setIsTreeOpen] = useState(false)
  const [isHistoryOpen, setIsHistoryOpen] = useState(false)
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
      <section className="branch-panel__section">
        <button
          type="button"
          className="branch-panel__toggle"
          onClick={() => setIsTreeOpen((open) => !open)}
          aria-expanded={isTreeOpen}
        >
          <span aria-hidden="true">{isTreeOpen ? '▴' : '▾'}</span>
          Dallanma haritası ({turnCount} tur)
        </button>

        {isTreeOpen && (
          <div className="branch-panel__section-body">
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
          </div>
        )}
      </section>

      <section className="branch-panel__section">
        <button
          type="button"
          className="branch-panel__toggle"
          onClick={() => setIsHistoryOpen((open) => !open)}
          aria-expanded={isHistoryOpen}
        >
          <span aria-hidden="true">{isHistoryOpen ? '▴' : '▾'}</span>
          Sohbet geçmişi
        </button>

        {isHistoryOpen && (
          <div className="branch-panel__section-body">
            <div className="branch-panel__history">
              <ConversationHistory
                messages={messages}
                activePath={activePath}
              />
            </div>
          </div>
        )}
      </section>
    </div>
  )
}

export default BranchTreePanel
