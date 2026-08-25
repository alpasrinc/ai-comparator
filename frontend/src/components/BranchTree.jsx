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
    <li className="branch-tree__node">
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

      {children.length > 0 && (
        <ul className="branch-tree__children">
          {children.map((child) => (
            <TreeNode
              key={child.message.id}
              node={child}
              activePath={activePath}
              onPreview={onPreview}
            />
          ))}
        </ul>
      )}
    </li>
  )
}

function BranchTree({ root, activePath, onPreview }) {
  if (!root) {
    return null
  }

  return (
    <ul className="branch-tree">
      <TreeNode node={root} activePath={activePath} onPreview={onPreview} />
    </ul>
  )
}

export default BranchTree
