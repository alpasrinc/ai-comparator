const PROVIDER_LABELS = {
  OPENAI: 'ChatGPT',
  ANTHROPIC: 'Claude',
  GEMINI: 'Gemini',
}

function TreeNode({ node, activePath, onPreview }) {
  const { message, children } = node
  const isActive = activePath.has(message.id)
  const isAssistant = message.role === 'ASSISTANT'

  return (
    <div className="branch-tree__node">
      <div className="branch-tree__self">
        {isAssistant ? (
          <button
            type="button"
            className={`branch-tree__badge branch-tree__badge--${message.provider.toLowerCase()} ${
              isActive
                ? 'branch-tree__badge--active'
                : 'branch-tree__badge--dim'
            }`}
            onClick={() => onPreview(message)}
          >
            {PROVIDER_LABELS[message.provider] ?? message.provider}
          </button>
        ) : (
          <span
            className={`branch-tree__question ${
              isActive ? 'branch-tree__question--active' : ''
            }`}
          >
            {message.content}
          </span>
        )}
      </div>

      {children.length > 0 && (
        <div className="branch-tree__row">
          {children.map((child) => (
            <div
              key={child.message.id}
              className={`branch-tree__branch ${
                activePath.has(child.message.id)
                  ? 'branch-tree__branch--connected'
                  : ''
              }`}
            >
              <TreeNode
                node={child}
                activePath={activePath}
                onPreview={onPreview}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function BranchTree({ root, activePath, onPreview }) {
  if (!root) {
    return null
  }

  return (
    <div className="branch-tree">
      <TreeNode node={root} activePath={activePath} onPreview={onPreview} />
    </div>
  )
}

export default BranchTree
