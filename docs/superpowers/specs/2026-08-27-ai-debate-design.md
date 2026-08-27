# AI Münazara (Debate) — Tasarım Dokümanı

**Tarih:** 2026-08-27
**Durum:** Onaylandı, uygulama planı bekleniyor

## Amaç

Kullanıcının verdiği bir konu üzerine seçili yapay zekâlar (OpenAI, Anthropic/Claude,
Gemini) tur tabanlı bir münazara yürütür; birbirlerinin cevaplarını eleştirip revize
eder ve sonunda tarafsız bir sentezci tek bir **net ortak cevap** üretir. Kullanıcı tüm
münazarayı canlı olarak, dikey bir zaman akışında izler.

Bu, mevcut "yan yana karşılaştırma" özelliğinden **ayrı** bir deneyimdir: ayrı sekme,
ayrı veri modeli, ayrı geçmiş listesi.

## Alınan Kararlar (brainstorming özeti)

| Konu | Karar |
| --- | --- |
| Tartışma mekaniği | Tur tabanlı multi-agent debate |
| Tur sayısı | Kullanıcı seçer, sabit; **1–5 arası** (varsayılan 2), üst limit token maliyeti için |
| Sentez | Kullanıcı sentezciyi seçer (3 sağlayıcıdan biri, katılımcı olması şart değil); sentezciye **tarafsız moderatör** talimatı verilir |
| Katılımcılar | Kullanıcı seçer, varsayılan üçü işaretli, **min 2** |
| Arayüz | Dikey zaman akışı (transcript) |
| Kalıcılık | Ayrı `Debate` veri modeli |
| Giriş noktası | Ayrı "Münazara" sekmesi + başlatma ekranı |
| Tur içi akış | Paralel turlar — her AI önceki turun cevaplarını görür, tur içinde paralel çalışır |

## Genel Akış

```
Münazara sekmesi → Başlatma ekranı → Canlı münazara akışı → Ortak cevap
```

### Münazara mekaniği

- **Tur 1:** Her katılımcı konuya bağımsız ilk cevabını verir (paralel).
- **Tur 2…N:** Her katılımcı *önceki turun* tüm katılımcı cevaplarını görür, eleştirip
  kendi cevabını revize eder. Tur içi paralel, turlar arası sıralı (tur N, tur N−1
  tamamlanmadan başlamaz).
- **Sentez:** Seçilen sentezci tüm transkripti (tüm turlar, tüm katılımcılar) okur ve
  tarafsız moderatör talimatıyla tek net **Ortak Cevap** yazar.

Paralel turlar seçildiği için sıra önyargısı yoktur ve mevcut paralel + SSE altyapısına
birebir oturur.

## Kullanıcı Arayüzü

### Başlatma ekranı (`DebateLauncher`)

- **Konu** — çok satırlı metin kutusu (zorunlu)
- **Katılımcılar** — 3 checkbox (OpenAI / Claude / Gemini), varsayılan üçü işaretli,
  **min 2** (2'nin altına inince "Başlat" devre dışı)
- **Tur sayısı** — 1–5 seçici, varsayılan 2
- **Sentezci** — dropdown (3 sağlayıcıdan biri)
- **[Münazarayı Başlat]** düğmesi

### Münazara akışı (`DebateTranscript`) — dikey zaman akışı

```
┌─ Konu: "..." ──────────────────────────────┐
│  ▼ Tur 1                                     │
│    🟢 OpenAI    [canlı yazılıyor…]           │
│    🟤 Claude    ...                          │
│    🔵 Gemini    ...                          │
│  ▼ Tur 2 — eleştiri/revizyon                 │
│    🟢 OpenAI    ...                          │
│    ...                                        │
│  ⚖️  ORTAK CEVAP  (sentezci)                 │
└──────────────────────────────────────────────┘
```

- Canlı token stream'i; her tur tamamlanınca bir sonraki tur başlar.
- Her mesaj sağlayıcı rengiyle etiketlenir.
- Sentez adımı en altta tam genişlik, vurgulu.
- Geçmiş münazaralar bu sekmeye ait `DebateHistory` listesinde.

## Veri Modeli

Yeni Flyway migration: `V2__debate_schema.sql`.

### `debates`
- `id` (PK)
- `topic` (metin)
- `rounds` (int, 1–5)
- `synthesizer_provider` (enum: OPENAI/ANTHROPIC/GEMINI)
- `status` (enum: RUNNING / COMPLETED / FAILED)
- `final_answer` (LONGTEXT, nullable — sentez tamamlanınca dolar)
- `created_at`, `updated_at`

### `debate_participants`
- `debate_id` (FK)
- `provider` (enum) — münazaraya katılan sağlayıcılar kümesi
- (JPA tarafında `Debate` içinde `@ElementCollection Set<AiProviderType>`)

### `debate_messages`
- `id` (PK)
- `debate_id` (FK, indexli)
- `round_number` (int; sentez mesajı için NULL)
- `provider` (enum)
- `role` (enum: PARTICIPANT / SYNTHESIS)
- `content` (LONGTEXT)
- `created_at`

## Backend Mimari

Mevcut desenleri (paralel `CompletableFuture` + `aiExecutor`, SSE `SseEmitter`) birebir izler.

### Entity & enum
- `Debate`, `DebateMessage`
- `DebateStatus` (RUNNING, COMPLETED, FAILED)
- `DebateMessageRole` (PARTICIPANT, SYNTHESIS)
- Katılımcılar: `Debate` içinde `@ElementCollection Set<AiProviderType>`

### Repository
- `DebateRepository`, `DebateMessageRepository`

### Servisler (sorumluluk ayrımı)
- **`DebateService`** — kalıcılık/CRUD: münazara oluştur, tur mesajlarını kaydet, sentezi
  kaydet, listele, detay getir.
- **`DebateOrchestrator`** — münazarayı yürütür ve SSE ile stream eder. Mevcut `providers`
  listesi ile `aiExecutor`'ı yeniden kullanır. Tur döngüsünü yönetir: her tur içinde seçili
  katılımcıları paralel stream eder, tur bitince sonrakine geçer, en sonda sentezciyi
  stream eder.
- **`DebatePromptBuilder`** — saf, izole, kolay test edilir:
  - Tur-1 promptu: katılımcıya konu + rolü verilir.
  - Tur-N promptu: önceki turun tüm katılımcı cevapları transkript olarak gömülür +
    "eleştir ve revize et" talimatı.
  - Sentez promptu: tüm transkript + "tarafsız moderatörsün, kimseyi kayırma, tek net
    ortak cevap üret" talimatı.

### Controller — `DebateController`
- `POST /api/debates/stream` — münazarayı başlat ve SSE ile stream et
- `GET /api/debates` — geçmiş münazara listesi (özet)
- `GET /api/debates/{id}` — münazara detayı (tam transkript + ortak cevap)

### DTO'lar
- `DebateRequest` (topic, participants, rounds, synthesizer)
- Stream event'leri:
  - `DebateStartEvent` (debateId)
  - `DebateRoundStartEvent` (roundNumber)
  - `DebateTokenEvent` (roundNumber, provider, delta)
  - `DebateParticipantDoneEvent` (roundNumber, provider, messageId, content)
  - `DebateParticipantErrorEvent` (roundNumber, provider, message)
  - `DebateRoundDoneEvent` (roundNumber)
  - `DebateSynthesisTokenEvent` (provider, delta)
  - `DebateSynthesisDoneEvent` (provider, messageId, content)
  - `DebateDoneEvent` (debateId, status)
- `DebateSummaryResponse`, `DebateDetailResponse`

### Ortak SSE yardımcısı
`AiComparisonService.sendEvent` (kilit + hata yakalama) mantığı münazarada da gerekli.
Küçük bir `SseSupport` yardımcısına çıkarılır; hem `AiComparisonService` hem
`DebateOrchestrator` kullanır. Mevcut kodda yapılan tek iyileştirme budur.

## Frontend Mimari

### Sekme yapısı
`App.jsx` şu an tek ekran, ~400 satır. Mevcut karşılaştırma gövdesi `CompareView`
bileşenine taşınır (davranış birebir korunur). `App` şu kabuğa iner:
sidebar + sekme çubuğu (**Karşılaştırma | Münazara**) + aktif görünüm.

### Yeni bileşenler
- **`CompareView`** — mevcut karşılaştırma mantığının taşındığı bileşen
- **`DebateView`** — münazara durumu + stream yönetimi (container)
- **`DebateLauncher`** — başlatma ekranı (konu, katılımcı, tur, sentezci)
- **`DebateTranscript`** — dikey zaman akışı (turlar + katılımcı kartları + ortak cevap)
- **`DebateHistory`** — geçmiş münazara listesi (münazara sekmesine ait)

### `api.js` eklemeleri
- `startDebateStream(request, handlers)` — SSE; mevcut `streamCompareMessage` desenini
  yeniden kullanır (AbortController + idle timeout + `parseSseEvent`)
- `getDebates()`, `getDebate(id)`

## Hata Yönetimi

Mevcut davranışı yeniden kullanır (timeout mesajları, boş cevap tespiti, SSE idle-timeout).

- **Katılımcı tur hatası:** O katılımcının o turdaki kartında hata gösterilir; münazara
  diğer katılımcılarla devam eder. Sonraki tur, hata veren katılımcının o turdaki
  eksikliğini tolere eder (transkriptte "cevap alınamadı" olarak geçer).
- **Sentezci hatası:** Transkript kaydedilir, `final_answer` boş kalır; kullanıcıya hata +
  "sentezi tekrar dene" gösterilir. Münazara `COMPLETED` (sentez hatalı) işaretlenir.
- **Timeout:** Mevcut `ai.request-timeout-seconds` ve SSE idle-timeout aynen kullanılır.

### `status` durumları — net tanım
- **RUNNING:** Münazara başladı, henüz bitmedi.
- **COMPLETED:** Turlar tamamlandı ve sentez adımına ulaşıldı (sentez başarılı da olsa,
  yalnızca sentez adımı hata verse de — transkript eksiksiz kaydedildi).
- **FAILED:** Münazara anlamlı şekilde ilerleyemedi; örn. **tur 1'de tüm katılımcılar**
  hata verdi (geçerli bir transkript oluşmadı, sentez yapılacak içerik yok).

## Test Stratejisi

Mevcut test stilini izler (backend JUnit, frontend Vitest).

### Backend
- `DebatePromptBuilder` birim testleri:
  - Tur-N promptu önceki turun transkriptini içeriyor mu?
  - Sentez promptu tarafsız moderatör talimatını içeriyor mu?
- `DebateService` kalıcılık testleri (oluştur, mesaj kaydet, detay getir).
- `DebateOrchestrator` testi: sahte `AiProvider` implementasyonu ile tur sıralaması ve
  doğru SSE event sırası doğrulanır.

### Frontend
- `DebateLauncher` doğrulama: min 2 katılımcı, tur 1–5 sınırı, boş konu engeli.
- `DebateTranscript` render: turlar ve ortak cevap doğru gösteriliyor mu.
- `api.js` SSE ayrıştırma: debate event'leri doğru handler'lara gidiyor mu.

## Kapsam Dışı (YAGNI — sonraki sürümler)

- Dinamik konsensüs (hemfikirlikte erken çıkış) — şimdilik sabit tur.
- Sentezi yeniden çalıştırma butonu (backend hatayı tolere eder ama UI'da retry ilk
  sürümde opsiyonel).
- Münazara paylaşımı/export.
- Sıralı münazara modu (tur içi zincirleme).
