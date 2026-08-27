const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const DEFAULT_TIMEOUT_MS = 35_000
const STREAM_IDLE_TIMEOUT_MS = 45_000

async function request(path, options = {}) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(
    () => controller.abort(),
    DEFAULT_TIMEOUT_MS,
  )

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      signal: controller.signal,
    })

    if (!response.ok) {
      const errorBody = await response.json().catch(() => null)
      const message =
        errorBody?.detail ??
        errorBody?.message ??
        `İstek başarısız oldu: HTTP ${response.status}`

      throw new Error(message)
    }

    return response.json()
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('İstek zaman aşımına uğradı. Tekrar deneyin.', {
        cause: error,
      })
    }

    if (error instanceof TypeError) {
      throw new Error('Backend bağlantısı kurulamadı.', { cause: error })
    }

    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export function getHealth() {
  return request('/api/health')
}

export function compareMessage(message, conversationId = null) {
  return request('/api/chat/compare', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId,
      message,
    }),
  })
}

function parseSseEvent(rawEvent) {
  const eventNameMatch = rawEvent.match(/^event:\s*(.+)$/m)
  const dataLines = rawEvent
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())

  if (!eventNameMatch || dataLines.length === 0) {
    return null
  }

  return {
    name: eventNameMatch[1].trim(),
    data: JSON.parse(dataLines.join('\n')),
  }
}

export async function streamCompareMessage(message, conversationId, handlers) {
  const controller = new AbortController()
  let idleTimeoutId = null

  const resetIdleTimeout = () => {
    window.clearTimeout(idleTimeoutId)
    idleTimeoutId = window.setTimeout(
      () => controller.abort(),
      STREAM_IDLE_TIMEOUT_MS,
    )
  }

  resetIdleTimeout()

  try {
    const response = await fetch(`${API_BASE_URL}/api/chat/compare/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ conversationId, message }),
      signal: controller.signal,
    })

    if (!response.ok || !response.body) {
      const errorBody = await response.json().catch(() => null)
      const errorMessage =
        errorBody?.detail ??
        errorBody?.message ??
        `İstek başarısız oldu: HTTP ${response.status}`

      throw new Error(errorMessage)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()

      if (done) {
        break
      }

      resetIdleTimeout()
      buffer += decoder.decode(value, { stream: true })

      const rawEvents = buffer.split('\n\n')
      buffer = rawEvents.pop() ?? ''

      for (const rawEvent of rawEvents) {
        const event = parseSseEvent(rawEvent)

        if (event) {
          handlers[event.name]?.(event.data)
        }
      }
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('İstek zaman aşımına uğradı. Tekrar deneyin.', {
        cause: error,
      })
    }

    if (error instanceof TypeError) {
      throw new Error('Backend bağlantısı kurulamadı.', { cause: error })
    }

    throw error
  } finally {
    window.clearTimeout(idleTimeoutId)
  }
}

export function selectActiveMessage(conversationId, messageId) {
  return request(
    `/api/conversations/${conversationId}/active-message`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ messageId }),
    },
  )
}

export function getConversations() {
  return request('/api/conversations')
}

export function getConversation(conversationId) {
  return request(`/api/conversations/${conversationId}`)
}

export function retryProvider(conversationId, userMessageId, provider) {
  return request('/api/chat/retry', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId,
      userMessageId,
      provider,
    }),
  })
}

export function getDebates() {
  return request('/api/debates')
}

export function getDebate(debateId) {
  return request(`/api/debates/${debateId}`)
}

export async function startDebateStream(debateRequest, handlers) {
  const controller = new AbortController()
  let idleTimeoutId = null

  const resetIdleTimeout = () => {
    window.clearTimeout(idleTimeoutId)
    idleTimeoutId = window.setTimeout(
      () => controller.abort(),
      STREAM_IDLE_TIMEOUT_MS,
    )
  }

  resetIdleTimeout()

  try {
    const response = await fetch(`${API_BASE_URL}/api/debates/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(debateRequest),
      signal: controller.signal,
    })

    if (!response.ok || !response.body) {
      const errorBody = await response.json().catch(() => null)
      const errorMessage =
        errorBody?.detail ??
        errorBody?.message ??
        `İstek başarısız oldu: HTTP ${response.status}`

      throw new Error(errorMessage)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()

      if (done) {
        break
      }

      resetIdleTimeout()
      buffer += decoder.decode(value, { stream: true })

      const rawEvents = buffer.split('\n\n')
      buffer = rawEvents.pop() ?? ''

      for (const rawEvent of rawEvents) {
        const event = parseSseEvent(rawEvent)

        if (event) {
          handlers[event.name]?.(event.data)
        }
      }
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('İstek zaman aşımına uğradı. Tekrar deneyin.', {
        cause: error,
      })
    }

    if (error instanceof TypeError) {
      throw new Error('Backend bağlantısı kurulamadı.', { cause: error })
    }

    throw error
  } finally {
    window.clearTimeout(idleTimeoutId)
  }
}
