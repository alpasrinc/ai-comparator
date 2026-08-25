export function buildConversationTree(messages, activeMessageId) {
  if (!messages || messages.length === 0) {
    return { root: null, activePath: new Set() }
  }

  const byId = new Map(messages.map((message) => [message.id, message]))

  const latestByParentAndProvider = new Map()

  for (const message of messages) {
    if (message.role !== 'ASSISTANT') {
      continue
    }

    const key = `${message.parentMessageId}:${message.provider}`
    const existing = latestByParentAndProvider.get(key)

    if (
      !existing ||
      new Date(message.createdAt) > new Date(existing.createdAt)
    ) {
      latestByParentAndProvider.set(key, message)
    }
  }

  const visibleAssistantIds = new Set(
    Array.from(latestByParentAndProvider.values()).map(
      (message) => message.id,
    ),
  )

  const isVisible = (message) =>
    message.role === 'USER' || visibleAssistantIds.has(message.id)

  const childrenByParentId = new Map()

  for (const message of messages) {
    if (!isVisible(message)) {
      continue
    }

    const key = message.parentMessageId
    if (!childrenByParentId.has(key)) {
      childrenByParentId.set(key, [])
    }
    childrenByParentId.get(key).push(message)
  }

  for (const children of childrenByParentId.values()) {
    children.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
  }

  function buildNode(message) {
    const children = (childrenByParentId.get(message.id) ?? []).map(
      buildNode,
    )
    return { message, children }
  }

  const rootMessage = messages.find(
    (message) => message.parentMessageId === null && message.role === 'USER',
  )

  const root = rootMessage ? buildNode(rootMessage) : null

  const activePath = new Set()
  let current =
    activeMessageId != null ? byId.get(activeMessageId) : undefined

  while (current) {
    activePath.add(current.id)
    current =
      current.parentMessageId != null
        ? byId.get(current.parentMessageId)
        : undefined
  }

  return { root, activePath }
}
