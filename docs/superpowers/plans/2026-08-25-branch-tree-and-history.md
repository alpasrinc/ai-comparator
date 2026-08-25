# Dallanma Haritası ve Tam Geçmiş Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kullanıcının aktif konuşmanın tam dallanma yapısını ve seçtiği yoldaki tüm geçmişi, AI panellerinin üstünde katlanır bir şeritte görmesini sağlamak.

**Architecture:** Backend değişmiyor — `GET /api/conversations/{id}` zaten tüm mesajları düz liste olarak dönüyor. Frontend'de saf bir `buildConversationTree` fonksiyonu bu listeyi ağaca çevirir; `BranchTreePanel` bileşeni bu ağacı ve aktif yol metnini katlanır bir şeritte gösterir.

**Tech Stack:** React 19, Vitest (yeni test altyapısı), mevcut `react-markdown`/`remark-gfm`/`remark-breaks`.

**Spec:** `docs/superpowers/specs/2026-08-25-branch-tree-and-history-design.md`

---

## Dosya Yapısı

- **Create:** `frontend/src/utils/conversationTree.js` — saf `buildConversationTree` fonksiyonu
- **Create:** `frontend/src/utils/conversationTree.test.js` — Vitest birim testleri
- **Create:** `frontend/src/components/BranchTree.jsx` — recursive ağaç render bileşeni
- **Create:** `frontend/src/components/ConversationHistory.jsx` — aktif yolun tam metin transkripti
- **Create:** `frontend/src/components/BranchTreePanel.jsx` — katlanır şerit, ağaç + geçmiş + önizleme kutusunu birleştirir
- **Modify:** `frontend/src/App.jsx` — `conversationMessages` state, `refreshConversationDetail`, `BranchTreePanel` render
- **Modify:** `frontend/src/App.css` — yeni stiller
- **Modify:** `frontend/vite.config.js` — Vitest test bloğu
- **Modify:** `frontend/package.json` — `vitest` devDependency + `test` script (npm install ile otomatik eklenecek)

---

### Task 1: Vitest kurulumu

**Files:**
- Modify: `frontend/vite.config.js`
- Modify: `frontend/package.json`

- [ ] **Step 1: Vitest'i yükle**

Run:
```bash
cd frontend
npm install --save-dev vitest
```

Expected: `package.json`'daki `devDependencies`'e `"vitest": "^<versiyon>"` otomatik eklenir.

- [ ] **Step 2: `test` script'ini ekle**

`frontend/package.json` içindeki `scripts` bloğunu güncelle:

```json
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "lint": "eslint .",
    "preview": "vite preview",
    "test": "vitest run"
  },
```

- [ ] **Step 3: Vite config'e test bloğu ekle**

`frontend/vite.config.js` dosyasının tamamını şu şekilde değiştir:

```js
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'node',
  },
})
```

- [ ] **Step 4: Boş bir smoke test ile kurulumu doğrula**

`frontend/src/utils/conversationTree.test.js` dosyasını geçici olarak şu içerikle oluştur:

```js
import { describe, expect, test } from 'vitest'

describe('vitest kurulumu', () => {
  test('çalışıyor', () => {
    expect(1 + 1).toBe(2)
  })
})
```

Run:
```bash
cd frontend
npm test
```

Expected: `1 passed` — Vitest kurulumu çalışıyor. Bu dosyanın içeriği Task 2'de gerçek testlerle değiştirilecek.

- [ ] **Step 5: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.js frontend/src/utils/conversationTree.test.js
git commit -m "chore: add vitest test runner to frontend"
```

---

### Task 2: `buildConversationTree` için başarısız testler (RED)

**Files:**
- Modify: `frontend/src/utils/conversationTree.test.js`

- [ ] **Step 1: Smoke testin yerine gerçek testleri yaz**

`frontend/src/utils/conversationTree.test.js` dosyasının tamamını şu içerikle değiştir:

```js
import { describe, expect, test } from 'vitest'
import { buildConversationTree } from './conversationTree'

function userMessage(id, parentMessageId, content, createdAt) {
  return {
    id,
    parentMessageId,
    role: 'USER',
    provider: null,
    content,
    createdAt,
  }
}

function assistantMessage(id, parentMessageId, provider, content, createdAt) {
  return {
    id,
    parentMessageId,
    role: 'ASSISTANT',
    provider,
    content,
    createdAt,
  }
}

describe('buildConversationTree', () => {
  test('boş mesaj listesinde çökmeden root: null döner', () => {
    const result = buildConversationTree([], null)

    expect(result.root).toBeNull()
    expect(result.activePath.size).toBe(0)
  })

  test('activeMessageId null olsa da mevcut mesajlardan ağaç kurar', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'OPENAI', 'Cevap', '2026-01-01T10:00:01Z'),
    ]

    const result = buildConversationTree(messages, null)

    expect(result.root).not.toBeNull()
    expect(result.root.message.id).toBe(1)
    expect(result.activePath.size).toBe(0)
  })

  test('tek turluk ağacı doğru kurar (1 USER + 3 ASSISTANT çocuk)', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'OPENAI', 'OpenAI cevabı', '2026-01-01T10:00:01Z'),
      assistantMessage(3, 1, 'ANTHROPIC', 'Claude cevabı', '2026-01-01T10:00:02Z'),
      assistantMessage(4, 1, 'GEMINI', 'Gemini cevabı', '2026-01-01T10:00:03Z'),
    ]

    const result = buildConversationTree(messages, 3)

    expect(result.root.message.id).toBe(1)
    expect(result.root.children).toHaveLength(3)
    expect(result.root.children.map((child) => child.message.id)).toEqual([
      2, 3, 4,
    ])
  })

  test('aktif yolu kökten aktif mesaja kadar doğru hesaplar', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'ANTHROPIC', 'Claude cevabı', '2026-01-01T10:00:01Z'),
      userMessage(3, 2, 'Örnek verir misin?', '2026-01-01T10:01:00Z'),
      assistantMessage(4, 3, 'OPENAI', 'Örnek cevap', '2026-01-01T10:01:01Z'),
    ]

    const result = buildConversationTree(messages, 4)

    expect(Array.from(result.activePath).sort()).toEqual([1, 2, 3, 4])
  })

  test('aynı asistan mesajından birden fazla kullanıcı dalını (gerçek dallanma) doğru kurar', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'OPENAI', 'OpenAI cevabı', '2026-01-01T10:00:01Z'),
      assistantMessage(3, 1, 'ANTHROPIC', 'Claude cevabı', '2026-01-01T10:00:02Z'),
      userMessage(4, 2, 'İlk dal sorusu', '2026-01-01T10:01:00Z'),
      assistantMessage(5, 4, 'GEMINI', 'İlk dal cevabı', '2026-01-01T10:01:01Z'),
      userMessage(6, 2, 'İkinci dal sorusu', '2026-01-01T10:02:00Z'),
      assistantMessage(7, 6, 'OPENAI', 'İkinci dal cevabı', '2026-01-01T10:02:01Z'),
    ]

    const result = buildConversationTree(messages, 7)

    const openAiChild = result.root.children.find(
      (child) => child.message.id === 2,
    )

    expect(openAiChild.children.map((child) => child.message.id)).toEqual([
      4, 6,
    ])
    expect(Array.from(result.activePath).sort()).toEqual([1, 2, 6, 7])
  })

  test('retry sonucu aynı sağlayıcıdan gelen eski cevabı ağaçtan eler', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(
        2,
        1,
        'ANTHROPIC',
        'İlk (hatalı) cevap',
        '2026-01-01T10:00:01Z',
      ),
      assistantMessage(
        3,
        1,
        'ANTHROPIC',
        'Retry sonrası cevap',
        '2026-01-01T10:00:05Z',
      ),
    ]

    const result = buildConversationTree(messages, 3)

    expect(result.root.children).toHaveLength(1)
    expect(result.root.children[0].message.id).toBe(3)
  })
})
```

- [ ] **Step 2: Testleri çalıştır ve başarısız olduğunu doğrula**

Run:
```bash
cd frontend
npm test
```

Expected: Tüm testler `Failed to resolve import "./conversationTree"` hatasıyla başarısız olur — dosya henüz mevcut değil. Bu beklenen RED durumudur.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/utils/conversationTree.test.js
git commit -m "test: add failing tests for buildConversationTree"
```

---

### Task 3: `buildConversationTree` implementasyonu (GREEN)

**Files:**
- Create: `frontend/src/utils/conversationTree.js`

- [ ] **Step 1: Fonksiyonu yaz**

`frontend/src/utils/conversationTree.js` dosyasını şu içerikle oluştur:

```js
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
```

- [ ] **Step 2: Testleri çalıştır ve geçtiğini doğrula**

Run:
```bash
cd frontend
npm test
```

Expected: `7 passed` — tüm testler geçer.

- [ ] **Step 3: Lint kontrolü**

Run:
```bash
cd frontend
npm run lint
```

Expected: Hata yok.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/utils/conversationTree.js
git commit -m "feat: implement buildConversationTree"
```

---

### Task 4: `BranchTree` bileşeni

**Files:**
- Create: `frontend/src/components/BranchTree.jsx`

- [ ] **Step 1: Bileşeni yaz**

`frontend/src/components/BranchTree.jsx` dosyasını şu içerikle oluştur:

```jsx
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
```

- [ ] **Step 2: Lint kontrolü**

Run:
```bash
cd frontend
npm run lint
```

Expected: Hata yok.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/BranchTree.jsx
git commit -m "feat: add BranchTree component"
```

---

### Task 5: `ConversationHistory` bileşeni

**Files:**
- Create: `frontend/src/components/ConversationHistory.jsx`

- [ ] **Step 1: Bileşeni yaz**

`frontend/src/components/ConversationHistory.jsx` dosyasını şu içerikle oluştur:

```jsx
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
```

- [ ] **Step 2: Lint kontrolü**

Run:
```bash
cd frontend
npm run lint
```

Expected: Hata yok.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ConversationHistory.jsx
git commit -m "feat: add ConversationHistory component"
```

---

### Task 6: `BranchTreePanel` bileşeni ve stiller

**Files:**
- Create: `frontend/src/components/BranchTreePanel.jsx`
- Modify: `frontend/src/App.css`

- [ ] **Step 1: Bileşeni yaz**

`frontend/src/components/BranchTreePanel.jsx` dosyasını şu içerikle oluştur:

```jsx
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
```

- [ ] **Step 2: Stilleri ekle**

`frontend/src/App.css` dosyasının sonuna şu bloğu ekle:

```css
.branch-panel {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.4);
  overflow: hidden;
}

.branch-panel__toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  color: #cbd5e1;
  background: transparent;
  border: none;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.branch-panel__toggle:hover {
  color: #f8fafc;
}

.branch-panel__body {
  padding: 4px 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.branch-panel__tree {
  max-height: 220px;
  overflow-y: auto;
  padding-top: 12px;
}

.branch-panel__history {
  max-height: 320px;
  overflow-y: auto;
}

.branch-panel__preview {
  padding: 12px 14px;
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
}

.branch-panel__preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #94a3b8;
  margin-bottom: 6px;
}

.branch-panel__preview-header button {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}

.branch-panel__preview p {
  margin: 0;
  color: #e2e8f0;
  line-height: 1.6;
  white-space: pre-wrap;
}

.branch-tree,
.branch-tree__children {
  list-style: none;
  margin: 0;
  padding-left: 0;
}

.branch-tree__children {
  padding-left: 20px;
  margin-top: 6px;
  border-left: 1px dashed rgba(148, 163, 184, 0.25);
}

.branch-tree__node {
  margin-bottom: 6px;
}

.branch-tree__question {
  display: block;
  padding: 4px 0;
  color: #64748b;
  font-size: 0.85rem;
}

.branch-tree__question--active {
  color: #e2e8f0;
  font-weight: 600;
}

.branch-tree__badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  margin: 2px 0;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
  background: rgba(148, 163, 184, 0.12);
  color: #94a3b8;
}

.branch-tree__badge--openai {
  --badge-color: #10b981;
}

.branch-tree__badge--anthropic {
  --badge-color: #f59e0b;
}

.branch-tree__badge--gemini {
  --badge-color: #60a5fa;
}

.branch-tree__badge--active {
  color: var(--badge-color);
  background: color-mix(in srgb, var(--badge-color) 16%, transparent);
  border-color: color-mix(in srgb, var(--badge-color) 45%, transparent);
}

.branch-tree__badge--dim {
  opacity: 0.55;
}

.branch-tree__badge--dim:hover {
  opacity: 1;
}

.conversation-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 4px;
}

.conversation-history__user {
  margin: 0;
  color: #94a3b8;
  font-size: 0.85rem;
}

.conversation-history__user::before {
  content: 'Siz: ';
  color: #64748b;
  font-weight: 700;
}

.conversation-history__assistant {
  padding: 10px 12px;
  background: rgba(30, 41, 59, 0.5);
  border-radius: 10px;
  color: #e2e8f0;
  font-size: 0.9rem;
  line-height: 1.6;
}

.conversation-history__provider {
  display: block;
  margin-bottom: 4px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #64748b;
}

.conversation-history__empty {
  margin: 0;
  color: #64748b;
  font-size: 0.85rem;
}
```

- [ ] **Step 3: Lint kontrolü**

Run:
```bash
cd frontend
npm run lint
```

Expected: Hata yok.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/BranchTreePanel.jsx frontend/src/App.css
git commit -m "feat: add BranchTreePanel component with tree and history styles"
```

---

### Task 7: `App.jsx`'e entegrasyon

**Files:**
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: Import ekle**

`frontend/src/App.jsx` dosyasının en üstündeki import bloğunu güncelle — mevcut:

```jsx
import { useEffect, useState } from 'react'
import AiPanel from './components/AiPanel'
import ChatInput from './components/ChatInput'
import ConversationSidebar from './components/ConversationSidebar'
```

şu şekilde değiştir:

```jsx
import { useEffect, useState } from 'react'
import AiPanel from './components/AiPanel'
import BranchTreePanel from './components/BranchTreePanel'
import ChatInput from './components/ChatInput'
import ConversationSidebar from './components/ConversationSidebar'
```

- [ ] **Step 2: `conversationMessages` state'i ekle**

Mevcut:

```jsx
  const [selectionError, setSelectionError] = useState('')
  const [retryingProvider, setRetryingProvider] = useState('')
```

şu şekilde değiştir:

```jsx
  const [selectionError, setSelectionError] = useState('')
  const [retryingProvider, setRetryingProvider] = useState('')
  const [conversationMessages, setConversationMessages] = useState([])
```

- [ ] **Step 3: `refreshConversationDetail` fonksiyonunu ekle**

`refreshConversations` fonksiyonunun hemen altına ekle. Mevcut:

```jsx
  async function refreshConversations() {
    try {
      const data = await getConversations()
      setConversations(data)
      setHistoryError('')
    } catch (requestError) {
      setHistoryError(requestError.message)
    }
  }
```

şu şekilde değiştir:

```jsx
  async function refreshConversations() {
    try {
      const data = await getConversations()
      setConversations(data)
      setHistoryError('')
    } catch (requestError) {
      setHistoryError(requestError.message)
    }
  }

  async function refreshConversationDetail(id) {
    if (!id) {
      setConversationMessages([])
      return
    }

    try {
      const conversation = await getConversation(id)
      setConversationMessages(conversation.messages)
    } catch {
      // Dallanma şeridi güncel veriyi gösteremeyebilir; ana akışı etkilemez.
    }
  }
```

- [ ] **Step 4: `handleOpenConversation` içinde mesajları da state'e yaz**

Mevcut:

```jsx
      setConversationId(conversation.id)
      setUserMessageId(latestUserMessage?.id ?? null)
      setSubmittedMessage(latestUserMessage?.content ?? '')
      setResponses(latestResponses)
      setSelectedMessageId(conversation.activeMessageId)
      setSelectedProvider(activeMessage?.provider ?? '')
```

şu şekilde değiştir:

```jsx
      setConversationId(conversation.id)
      setUserMessageId(latestUserMessage?.id ?? null)
      setSubmittedMessage(latestUserMessage?.content ?? '')
      setResponses(latestResponses)
      setSelectedMessageId(conversation.activeMessageId)
      setSelectedProvider(activeMessage?.provider ?? '')
      setConversationMessages(conversation.messages)
```

- [ ] **Step 5: `handleNewConversation` içinde mesajları sıfırla**

Mevcut:

```jsx
  function handleNewConversation() {
    setConversationId(null)
    setUserMessageId(null)
    setSubmittedMessage('')
    setResponses([])
    setSelectedMessageId(null)
    setSelectedProvider('')
    setComparisonError('')
    setSelectionError('')
    setHistoryError('')
  }
```

şu şekilde değiştir:

```jsx
  function handleNewConversation() {
    setConversationId(null)
    setUserMessageId(null)
    setSubmittedMessage('')
    setResponses([])
    setSelectedMessageId(null)
    setSelectedProvider('')
    setComparisonError('')
    setSelectionError('')
    setHistoryError('')
    setConversationMessages([])
  }
```

- [ ] **Step 6: `handleSend` içinde stream tamamlanınca geçmişi tazele**

`resolvedConversationId` isimli yerel bir değişkenle SSE `start` olayından gelen id'yi yakalamamız gerekiyor (React state güncellemesi asenkron olduğu için `conversationId` state'i fonksiyon içinde henüz güncellenmemiş olabilir). Mevcut:

```jsx
  async function handleSend(message) {
    setSubmittedMessage(message)
    setResponses(
      PROVIDERS.map((provider) => ({
        provider,
        content: '',
        messageId: null,
        error: null,
        streaming: true,
      })),
    )
    setSelectedMessageId(null)
    setSelectedProvider('')
    setUserMessageId(null)
    setComparisonError('')
    setSelectionError('')
    setIsLoading(true)

    try {
      await streamCompareMessage(message, conversationId, {
        start: (payload) => {
          setConversationId(payload.conversationId)
          setUserMessageId(payload.userMessageId)
        },
        token: ({ provider, delta }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, content: response.content + delta }
                : response,
            ),
          )
        },
        done: ({ provider, messageId, content }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, messageId, content, streaming: false }
                : response,
            ),
          )
        },
        error: ({ provider, message: errorMessage }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, error: errorMessage, streaming: false }
                : response,
            ),
          )
        },
      })

      await refreshConversations()
    } catch (requestError) {
      setComparisonError(requestError.message)
      setResponses((current) =>
        current.map((response) => ({ ...response, streaming: false })),
      )
    } finally {
      setIsLoading(false)
    }
  }
```

şu şekilde değiştir:

```jsx
  async function handleSend(message) {
    setSubmittedMessage(message)
    setResponses(
      PROVIDERS.map((provider) => ({
        provider,
        content: '',
        messageId: null,
        error: null,
        streaming: true,
      })),
    )
    setSelectedMessageId(null)
    setSelectedProvider('')
    setUserMessageId(null)
    setComparisonError('')
    setSelectionError('')
    setIsLoading(true)

    let resolvedConversationId = conversationId

    try {
      await streamCompareMessage(message, conversationId, {
        start: (payload) => {
          resolvedConversationId = payload.conversationId
          setConversationId(payload.conversationId)
          setUserMessageId(payload.userMessageId)
        },
        token: ({ provider, delta }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, content: response.content + delta }
                : response,
            ),
          )
        },
        done: ({ provider, messageId, content }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, messageId, content, streaming: false }
                : response,
            ),
          )
        },
        error: ({ provider, message: errorMessage }) => {
          setResponses((current) =>
            current.map((response) =>
              response.provider === provider
                ? { ...response, error: errorMessage, streaming: false }
                : response,
            ),
          )
        },
      })

      await refreshConversations()
      await refreshConversationDetail(resolvedConversationId)
    } catch (requestError) {
      setComparisonError(requestError.message)
      setResponses((current) =>
        current.map((response) => ({ ...response, streaming: false })),
      )
    } finally {
      setIsLoading(false)
    }
  }
```

- [ ] **Step 7: `handleSelect` içinde seçim sonrası geçmişi tazele**

Mevcut:

```jsx
      setSelectedMessageId(result.activeMessageId)
      setSelectedProvider(result.provider)
      await refreshConversations()
    } catch (requestError) {
      setSelectionError(requestError.message)
    } finally {
      setSelectingMessageId(null)
    }
```

şu şekilde değiştir:

```jsx
      setSelectedMessageId(result.activeMessageId)
      setSelectedProvider(result.provider)
      await refreshConversations()
      await refreshConversationDetail(conversationId)
    } catch (requestError) {
      setSelectionError(requestError.message)
    } finally {
      setSelectingMessageId(null)
    }
```

- [ ] **Step 8: `BranchTreePanel`'i JSX'e ekle**

AI panellerinin (`<section className="ai-grid" ...>`) hemen üstüne, `selectionError` bildiriminden sonra ekle. Mevcut:

```jsx
        {selectionError && (
          <div className="selection-notice selection-notice--error">
            {selectionError}
          </div>
        )}

        <section className="ai-grid" aria-label="Yapay zekâ cevapları">
```

şu şekilde değiştir:

```jsx
        {selectionError && (
          <div className="selection-notice selection-notice--error">
            {selectionError}
          </div>
        )}

        <BranchTreePanel
          messages={conversationMessages}
          activeMessageId={selectedMessageId}
        />

        <section className="ai-grid" aria-label="Yapay zekâ cevapları">
```

- [ ] **Step 9: Lint kontrolü**

Run:
```bash
cd frontend
npm run lint
```

Expected: Hata yok.

- [ ] **Step 10: Build kontrolü**

Run:
```bash
cd frontend
npm run build
```

Expected: Başarılı build, hata yok.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/App.jsx
git commit -m "feat: wire BranchTreePanel into App"
```

---

### Task 8: Tarayıcıda manuel doğrulama

**Files:** Yok — yalnızca doğrulama.

- [ ] **Step 1: Docker Compose ile uygulamayı ayağa kaldır (veya native çalıştır)**

Run:
```bash
docker compose up --build
```

(Gerekli environment değişkenleri için önceki README talimatlarına bakın.)

- [ ] **Step 2: Manuel senaryo**

`http://localhost:5173` adresinde:

1. Yeni bir mesaj gönderin, 3 cevap gelsin. "Dallanma haritası" şeridinin AI panellerinin üstünde göründüğünü doğrulayın.
2. Şeride tıklayın, açıldığını doğrulayın — 1 kullanıcı düğümü + 3 sağlayıcı rozeti görünmeli, hiçbiri henüz aktif (vurgulu) olmamalı.
3. Bir cevabı seçin ("Bu cevapla devam et"). Şeridi tekrar açın — seçtiğiniz rozetin artık vurgulu (aktif renkli), diğer ikisinin soluk olduğunu doğrulayın. Alttaki "Tam geçmiş" bölümünde seçtiğiniz cevabın metninin göründüğünü doğrulayın.
4. Soluk bir rozete tıklayın — o cevabın metninin küçük bir önizleme kutusunda göründüğünü, aktif dalın DEĞİŞMEDİĞİNİ doğrulayın.
5. Yeni bir mesaj gönderip devam edin (2. tur). Ağaçta artık 2 kullanıcı düğümü, geçmişte 2 tur metin olduğunu doğrulayın.
6. Sol menüden başka/eski bir konuşmayı açın — şeridin o konuşmanın verisiyle güncellendiğini doğrulayın.
7. "Yeni konuşma" butonuna basın — şeridin tamamen kaybolduğunu (turnCount 0) doğrulayın.

- [ ] **Step 3: Tespit edilen sorunları not al ve düzelt**

Herhangi bir görsel/mantıksal sorun bulunursa ilgili bileşende düzeltip Adım 9-11'deki lint/build/commit döngüsünü tekrarlayın.

---

## Spec Kapsama Kontrolü

- Katlanır şerit, AI panellerinin üstünde → Task 7, Step 8 ✅
- Ağaç görünümü (USER düğümleri + provider rozetleri, aktif/soluk ayrımı) → Task 4, Task 6 ✅
- Alternatif rozete tıklayınca önizleme (dal değiştirmeden) → Task 4 (`onPreview`), Task 6 (`previewMessage`) ✅
- Tam geçmiş (aktif yol, markdown render) → Task 5 ✅
- Gerçek çoklu dallanma (bir USER mesajının >1 USER çocuğu) desteği → Task 3 testi ✅
- Retry dedup (aynı sağlayıcıdan en yeni cevap) → Task 3 testi ✅
- Backend değişikliği yok → doğrulandı, hiçbir backend dosyası bu planda yok ✅
- `buildConversationTree` birim testleri → Task 2-3 ✅
