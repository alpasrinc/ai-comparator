# Akış İptali ve Münazara Rate Limit — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** İstemci bağlantıyı kestiğinde backend'in sağlayıcı çağrılarını durdurması, kullanıcıya bir Durdur butonu verilmesi ve `/api/debates/**` ucunun rate limit ile korunması.

**Architecture:** `SseSupport` bean'i, tek bir SSE akışının tüm durumunu (emitter + gönderim kilidi + iptal bayrağı) taşıyan istek-kapsamlı `StreamSession` nesnesiyle değiştirilir. Session'ı controller oluşturur ve servise geçirir. Orkestratörler turlar arasında ve token callback'lerinde iptal bayrağını okur; token callback'i `StreamCancelledException` fırlatarak akan SDK çağrısını ortasından keser. Frontend akış fonksiyonlarına dışarıdan `AbortSignal` verilir.

**Tech Stack:** Spring Boot 4 / Java 21, JUnit 5 + Mockito + AssertJ, React 19 + Vite, vitest.

**Spec:** `docs/superpowers/specs/2026-09-02-akis-iptali-ve-munazara-rate-limit-design.md`

---

## Spec'ten sapma (bilinçli)

Spec, `StreamSession`'ın orkestratör içinde oluşturulacağını ima ediyordu. Planda **controller oluşturuyor** ve servise parametre olarak geçiyor.

Gerekçe: `SseEmitter.onError` callback'ini bir unit test'ten tetiklemek mümkün değil — o callback'i Spring MVC'nin `ResponseBodyEmitterReturnValueHandler`'ı çağırır, emitter'ın kendisi değil. Session dışarıdan verilirse test `session.cancel()` diyerek iptali deterministik biçimde tetikleyebiliyor. Ayrıca emitter yaşam döngüsünün sahibi controller olur ki bu zaten daha doğru bir sınır.

---

## Dosya Yapısı

**Yeni:**
- `backend/src/main/java/com/example/aicomparator/service/StreamSession.java` — tek bir SSE akışının durumu: emitter, gönderim kilidi, iptal bayrağı. Gönderim başarısız olursa kendini iptal eder.
- `backend/src/main/java/com/example/aicomparator/service/StreamCancelledException.java` — token callback'inden fırlatılıp akan sağlayıcı çağrısını kesen işaret exception'ı.
- `backend/src/test/java/com/example/aicomparator/service/StreamSessionTests.java`

**Silinen:**
- `backend/src/main/java/com/example/aicomparator/service/SseSupport.java` — görevini `StreamSession` devralır.

**Değişen (backend):**
- `entity/DebateStatus.java` — `CANCELLED` değeri
- `entity/Debate.java` — `cancel()`
- `service/DebateService.java` — `markCancelled(Long)`
- `service/DebateOrchestrator.java` — session parametresi, iptal kontrol noktaları, kısmi kayıt
- `service/AiComparisonService.java` — session parametresi, kısmi kayıt
- `controller/DebateController.java`, `controller/ChatController.java` — session oluşturma
- `config/AiRateLimitFilter.java` — iki kova, yalnızca POST
- `resources/application.properties` — münazara kovası ayarları
- Testler: `DebateOrchestratorTests`, `AiComparisonServiceTests`, `AiRateLimitFilterTests`

**Değişen (frontend):**
- `services/api.js` — iki akış fonksiyonuna opsiyonel `signal`
- `components/ChatInput.jsx` — gönder butonu `isLoading` iken Durdur'a döner
- `components/CompareView.jsx` — `AbortController` ref'i, `handleStop`
- `components/AiPanel.jsx` — `Durduruldu` statüsü
- `components/DebateView.jsx` — `AbortController` ref'i, `handleStop`
- `components/DebateTranscript.jsx` — durduruldu sütun statüsü + Durdur butonu
- `components/DebateHistory.jsx` — `CANCELLED` rozeti
- `App.css` — `--stopped` statü renkleri, durdur butonu, geçmiş rozeti

**Dokümantasyon:**
- `CLAUDE.md` — "Rate limiting" ve "Streaming (SSE)" bölümleri
- `README.md` — özellik listesi

---

## Task 1: `CANCELLED` durumu

Bu görev sadece veri modelini açar; kendi testi yoktur çünkü test edilecek davranış yok (bir enum değeri ve bir setter). Task 3'teki iptal testi `markCancelled`'ı doğrular.

`DebateStatus` VARCHAR olarak saklandığı için **Flyway migration gerekmez**.

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/entity/DebateStatus.java`
- Modify: `backend/src/main/java/com/example/aicomparator/entity/Debate.java`
- Modify: `backend/src/main/java/com/example/aicomparator/service/DebateService.java`

- [ ] **Step 1: Enum'a `CANCELLED` ekle**

`DebateStatus.java` tamamı:

```java
package com.example.aicomparator.entity;

public enum DebateStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 2: `Debate.cancel()` ekle**

`Debate.java` içinde, mevcut `fail()` metodunun hemen altına:

```java
    public void cancel() {
        this.status = DebateStatus.CANCELLED;
    }
```

- [ ] **Step 3: `DebateService.markCancelled()` ekle**

`DebateService.java` içinde, mevcut `markFailed` metodunun hemen altına:

```java
    @Transactional
    public void markCancelled(Long debateId) {
        Debate debate = requireDebate(debateId);
        debate.cancel();
        debateRepository.save(debate);
    }
```

- [ ] **Step 4: Derlendiğini doğrula**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS (çıktı yok)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/entity/DebateStatus.java \
        backend/src/main/java/com/example/aicomparator/entity/Debate.java \
        backend/src/main/java/com/example/aicomparator/service/DebateService.java
git commit -m "feat(debate): add CANCELLED status"
```

---

## Task 2: `StreamSession` ve `StreamCancelledException`

**Files:**
- Create: `backend/src/main/java/com/example/aicomparator/service/StreamCancelledException.java`
- Create: `backend/src/main/java/com/example/aicomparator/service/StreamSession.java`
- Test: `backend/src/test/java/com/example/aicomparator/service/StreamSessionTests.java`

- [ ] **Step 1: Başarısız testi yaz**

`StreamSessionTests.java`:

```java
package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamSessionTests {

    @Test
    void sendsEventWhileClientIsConnected() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isTrue();
        assertThat(session.isCancelled()).isFalse();
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void cancelsItselfWhenSendFails() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("istemci gitti"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        assertThat(session.isCancelled()).isTrue();
    }

    @Test
    void doesNotSendAfterCancellation() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        StreamSession session = new StreamSession(emitter);

        session.cancel();
        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void abortIfCancelledThrowsOnlyAfterCancellation() {
        StreamSession session = new StreamSession(mock(SseEmitter.class));

        session.abortIfCancelled();

        session.cancel();
        assertThatThrownBy(session::abortIfCancelled)
                .isInstanceOf(StreamCancelledException.class);
    }
}
```

- [ ] **Step 2: Testin başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw test -Dtest=StreamSessionTests`
Expected: COMPILATION ERROR — `StreamSession` ve `StreamCancelledException` sınıfları yok

- [ ] **Step 3: `StreamCancelledException` yaz**

```java
package com.example.aicomparator.service;

/**
 * Token callback'inden fırlatılır. Amacı hata bildirmek değil, akan bir
 * sağlayıcı çağrısını ortasından kesmek: SDK'nın stream döngüsü callback
 * fırlattığında sonlanır.
 */
public class StreamCancelledException extends RuntimeException {

    public StreamCancelledException() {
        super("Akış istemci tarafından iptal edildi.");
    }
}
```

- [ ] **Step 4: `StreamSession` yaz**

```java
package com.example.aicomparator.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tek bir SSE akışının durumu: emitter, gönderim kilidi ve iptal bayrağı.
 *
 * <p>İstemci bağlantıyı kestiğinde (sekme kapatma, Durdur, ağ kopması) iptal
 * bayrağı kalkar. Orkestratörler bu bayrağı turlar arasında ve token
 * callback'lerinde okuyup kalan sağlayıcı çağrılarını yapmaz — aksi halde
 * kimse dinlemiyorken ücretli çağrılar sürer.
 */
public final class StreamSession {

    private static final Logger log =
            LoggerFactory.getLogger(StreamSession.class);

    private final SseEmitter emitter;
    private final Object lock = new Object();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public StreamSession(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onError(throwable -> cancel());
        emitter.onCompletion(this::cancel);
        emitter.onTimeout(() -> {
            cancel();
            emitter.complete();
        });
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Olayı gönderir. İptal edilmişse hiç göndermez; gönderim başarısız
     * olursa istemcinin gittiğini varsayıp akışı iptal eder.
     *
     * @return olay gerçekten gönderildiyse true
     */
    public boolean send(String eventName, Object data) {
        if (cancelled.get()) {
            return false;
        }

        synchronized (lock) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(eventName)
                                .data(data, MediaType.APPLICATION_JSON)
                );
                return true;
            } catch (Exception exception) {
                log.debug(
                        "SSE gönderimi başarısız, akış iptal ediliyor: {}",
                        exception.getMessage()
                );
                cancel();
                return false;
            }
        }
    }

    /**
     * Token callback'lerinden çağrılır: iptal edilmişse sağlayıcı akışını
     * exception fırlatarak keser.
     */
    public void abortIfCancelled() {
        if (cancelled.get()) {
            throw new StreamCancelledException();
        }
    }

    public void complete() {
        synchronized (lock) {
            emitter.complete();
        }
    }
}
```

- [ ] **Step 5: Testin geçtiğini doğrula**

Run: `cd backend && ./mvnw test -Dtest=StreamSessionTests`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/service/StreamSession.java \
        backend/src/main/java/com/example/aicomparator/service/StreamCancelledException.java \
        backend/src/test/java/com/example/aicomparator/service/StreamSessionTests.java
git commit -m "feat(sse): add StreamSession with client-gone cancellation"
```

---

## Task 3: `DebateOrchestrator` iptal kontrol noktaları

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/service/DebateOrchestrator.java`
- Modify: `backend/src/main/java/com/example/aicomparator/controller/DebateController.java`
- Test: `backend/src/test/java/com/example/aicomparator/service/DebateOrchestratorTests.java`

- [ ] **Step 1: Başarısız testi yaz**

`DebateOrchestratorTests.java` içine, mevcut `marksFailedWhenAllParticipantsFailInFirstRound` testinin altına ekle. Test, 1. turun token callback'i içinde `session.cancel()` çağırarak iptali deterministik biçimde tetikler:

```java
    @Test
    void stopsAfterCurrentRoundWhenClientDisconnects() throws Exception {
        var emitter =
                new org.springframework.web.servlet.mvc.method.annotation
                        .SseEmitter(5000L);
        StreamSession session = new StreamSession(emitter);

        AiProvider openAi = cancellingProvider(
                AiProviderType.OPENAI, "kısmi cevap", session);
        AiProvider gemini = cancellingProvider(
                AiProviderType.GEMINI, "kısmi cevap", session);

        DebateService debateService = mock(DebateService.class);
        when(debateService.createDebate(any())).thenReturn(55L);
        when(debateService.saveParticipantMessage(
                eq(55L), anyInt(), any(), any(), any())).thenReturn(1L);

        DebateOrchestrator orchestrator = new DebateOrchestrator(
                List.of(openAi, gemini),
                executor,
                debateService,
                new DebatePromptBuilder(),
                5,
                10
        );

        DebateRequest request = new DebateRequest(
                "Konu",
                List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                3,
                AiProviderType.OPENAI,
                ResponseIntensity.MEDIUM
        );

        orchestrator.runDebate(request, session).get();

        // 1. tur çalıştı, 2. ve 3. tur hiç başlamadı
        org.mockito.Mockito.verify(openAi, org.mockito.Mockito.times(1))
                .streamMessage(any(), any(), any());
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.times(1))
                .streamMessage(any(), any(), any());

        // sentez hiç yapılmadı
        org.mockito.Mockito.verify(openAi, org.mockito.Mockito.never())
                .streamSynthesisMessage(any(), any(), any());
        org.mockito.Mockito.verify(debateService, org.mockito.Mockito.never())
                .saveSynthesisMessage(any(), any(), any(), any());

        // münazara iptal işaretlendi, kısmi sonuç saklandı
        org.mockito.Mockito.verify(debateService).markCancelled(55L);
        org.mockito.Mockito.verify(debateService,
                        org.mockito.Mockito.atLeastOnce())
                .saveParticipantMessage(eq(55L), eq(1), any(), any(), any());
    }
```

Aynı dosyanın altındaki yardımcı metotların yanına ekle:

```java
    /**
     * Token'ı yayar, sonra istemcinin gittiğini simüle eder. Böylece 1. tur
     * tamamlanır ama 2. tur hiç başlamaz.
     */
    private AiProvider cancellingProvider(
            AiProviderType type,
            String chunk,
            StreamSession session
    ) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(type);
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept(chunk);
            session.cancel();
            return TokenUsage.EMPTY;
        }).when(provider).streamMessage(any(), any(), any());
        return provider;
    }
```

- [ ] **Step 2: Mevcut testlerin çağrılarını yeni imzaya taşı**

Aynı dosyada iki mevcut testte iki değişiklik var:

1. `new DebateOrchestrator(...)` çağrılarından `new SseSupport(),` satırını **sil** (her iki testte de).
2. `orchestrator.runDebate(request, emitter).get();` satırlarını şuna çevir:

```java
        orchestrator.runDebate(request, new StreamSession(emitter)).get();
```

- [ ] **Step 3: Testin başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw test -Dtest=DebateOrchestratorTests`
Expected: COMPILATION ERROR — `runDebate(DebateRequest, StreamSession)` imzası ve `markCancelled` yok

- [ ] **Step 4: `DebateOrchestrator`'ı güncelle**

Dört değişiklik:

**(a)** `SseSupport` alanını ve constructor parametresini sil. Constructor imzası:

```java
    public DebateOrchestrator(
            List<AiProvider> providers,
            ExecutorService aiExecutor,
            DebateService debateService,
            DebatePromptBuilder promptBuilder,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds,
            @Value("${ai.synthesis-timeout-seconds:90}")
            long synthesisTimeoutSeconds
    ) {
        for (AiProvider provider : providers) {
            providersByType.put(provider.getProviderType(), provider);
        }
        this.aiExecutor = aiExecutor;
        this.debateService = debateService;
        this.promptBuilder = promptBuilder;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.synthesisTimeoutSeconds = synthesisTimeoutSeconds;
    }
```

`private final SseSupport sseSupport;` alanını sil.

**(b)** `runDebate` — emitter yerine session alır, emitter callback'leri artık `StreamSession` içinde kayıtlı olduğu için buradan silinir:

```java
    public CompletableFuture<Void> runDebate(
            DebateRequest request,
            StreamSession session
    ) {
        Long debateId = debateService.createDebate(request);

        session.send("start", new DebateStartEvent(debateId));

        return CompletableFuture.runAsync(
                () -> execute(request, debateId, session),
                aiExecutor
        );
    }
```

**(c)** `execute` — tur döngüsünün başında ve sentez öncesinde iptal kontrolü:

```java
    private void execute(
            DebateRequest request,
            Long debateId,
            StreamSession session
    ) {
        ResponseIntensity intensity =
                ResponseIntensity.orDefault(request.intensity());
        try {
            List<Map<AiProviderType, String>> transcript = new ArrayList<>();

            for (int round = 1; round <= request.rounds(); round++) {
                if (session.isCancelled()) {
                    debateService.markCancelled(debateId);
                    return;
                }

                session.send("round-start", new DebateRoundStartEvent(round));

                Map<AiProviderType, String> roundResult = runRound(
                        request, debateId, round, transcript, intensity,
                        session);
                transcript.add(roundResult);

                session.send("round-done", new DebateRoundDoneEvent(round));

                boolean allBlank = roundResult.values().stream()
                        .allMatch(value -> value == null || value.isBlank());
                if (round == 1 && allBlank) {
                    debateService.markFailed(debateId);
                    session.send("done", new DebateDoneEvent(debateId,
                            DebateStatus.FAILED.name()));
                    return;
                }
            }

            if (session.isCancelled()) {
                debateService.markCancelled(debateId);
                return;
            }

            runSynthesis(request, debateId, transcript, intensity, session);

            session.send("done", new DebateDoneEvent(debateId,
                    DebateStatus.COMPLETED.name()));
        } catch (Exception exception) {
            log.warn("Münazara yürütülürken hata: {}",
                    exception.getMessage(), exception);
        } finally {
            session.complete();
        }
    }
```

**(d)** `runRound` ve `streamParticipant` — `emitter, lock` yerine `session`. `streamParticipant`'ta iki kritik değişiklik: `accumulated` lambda'nın **dışına** taşınır (yoksa exception fırladığında kısmi metin kaybolur) ve token callback'i iptal kontrolü yapar.

```java
    private Map<AiProviderType, String> runRound(
            DebateRequest request,
            Long debateId,
            int round,
            List<Map<AiProviderType, String>> transcript,
            ResponseIntensity intensity,
            StreamSession session
    ) {
        List<CompletableFuture<Map.Entry<AiProviderType, String>>> futures =
                new ArrayList<>();

        for (AiProviderType type : request.participants()) {
            if (session.isCancelled()) {
                break;
            }

            AiProvider provider = resolveProvider(type);
            String prompt = round == 1
                    ? promptBuilder.buildFirstRoundPrompt(request.topic(), type)
                    : promptBuilder.buildCritiqueRoundPrompt(
                            request.topic(), type, transcript);

            futures.add(streamParticipant(
                    debateId, round, type, provider, prompt, intensity,
                    session));
        }

        Map<AiProviderType, String> result = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<AiProviderType, String>> future
                : futures) {
            Map.Entry<AiProviderType, String> entry = future.join();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private CompletableFuture<Map.Entry<AiProviderType, String>>
            streamParticipant(
                    Long debateId,
                    int round,
                    AiProviderType type,
                    AiProvider provider,
                    String prompt,
                    ResponseIntensity intensity,
                    StreamSession session
            ) {
        String name = type.name();
        AtomicReference<TokenUsage> usageRef =
                new AtomicReference<>(TokenUsage.EMPTY);
        // Lambda'nın dışında: iptal exception'ı fırladığında o ana kadar
        // biriken metnin kaybolmaması için.
        StringBuilder accumulated = new StringBuilder();

        return CompletableFuture.supplyAsync(() -> {
                    TokenUsage usage = provider.streamMessage(
                            prompt, intensity, delta -> {
                                session.abortIfCancelled();
                                accumulated.append(delta);
                                session.send("token",
                                        new DebateTokenEvent(round, name, delta));
                            });
                    usageRef.set(usage);
                    return accumulated.toString();
                }, aiExecutor)
                .orTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .handle((content, throwable) -> {
                    if (throwable == null && content != null
                            && !content.isBlank()) {
                        TokenUsage usage = usageRef.get();
                        Long messageId = debateService.saveParticipantMessage(
                                debateId, round, type, content, usage);
                        session.send("participant-done",
                                new DebateParticipantDoneEvent(
                                        round, name, messageId, content, usage));
                        return Map.entry(type, content);
                    }

                    String partial = accumulated.toString();
                    if (isCancellation(throwable) && !partial.isBlank()) {
                        // İstemci gitti ama token'lar zaten ödendi: sakla.
                        debateService.saveParticipantMessage(
                                debateId, round, type, partial, usageRef.get());
                        return Map.entry(type, partial);
                    }

                    log.warn("{} katılımcısı tur {} sırasında hata/boş cevap: {}",
                            name, round,
                            throwable == null ? "boş" : throwable.getMessage());
                    session.send("participant-error",
                            new DebateParticipantErrorEvent(round, name,
                                    "Bu yapay zekâdan yanıt alınamadı."));
                    return Map.entry(type, "");
                });
    }

    private static boolean isCancellation(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException
                ? throwable.getCause()
                : throwable;
        return cause instanceof StreamCancelledException;
    }
```

`java.util.concurrent.CompletionException` import'unu ekle.

**(e)** `runSynthesis` — `emitter, lock` yerine `session`:

```java
    private void runSynthesis(
            DebateRequest request,
            Long debateId,
            List<Map<AiProviderType, String>> transcript,
            ResponseIntensity intensity,
            StreamSession session
    ) {
        AiProviderType synthType = request.synthesizer();
        AiProvider provider = resolveProvider(synthType);
        String name = synthType.name();
        String prompt = promptBuilder.buildSynthesisPrompt(
                request.topic(), transcript);

        StringBuilder accumulated = new StringBuilder();
        AtomicReference<TokenUsage> usageRef =
                new AtomicReference<>(TokenUsage.EMPTY);
        try {
            CompletableFuture.runAsync(() ->
                    usageRef.set(provider.streamSynthesisMessage(
                            prompt, intensity, delta -> {
                                session.abortIfCancelled();
                                accumulated.append(delta);
                                session.send("token",
                                        new DebateTokenEvent(0, name, delta));
                            })), aiExecutor)
                    .orTimeout(synthesisTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (Exception exception) {
            log.warn("Sentez sırasında hata: {}", exception.getMessage());
        }

        String content = accumulated.toString();
        if (content.isBlank()) {
            debateService.markCompletedWithoutSynthesis(debateId);
            session.send("participant-error",
                    new DebateParticipantErrorEvent(0, name,
                            "Ortak cevap üretilemedi. Tekrar deneyin."));
            return;
        }

        TokenUsage usage = usageRef.get();
        Long messageId = debateService.saveSynthesisMessage(
                debateId, synthType, content, usage);
        session.send("synthesis-done",
                new DebateSynthesisDoneEvent(name, messageId, content, usage));
    }
```

`SseEmitter` ve `SseSupport` artık kullanılmadığı için ilgili import'ları sil.

- [ ] **Step 5: `DebateController`'ı güncelle**

`startDebate` metodunu değiştir:

```java
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter startDebate(@Valid @RequestBody DebateRequest request) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);
        debateOrchestrator.runDebate(request, new StreamSession(emitter));
        return emitter;
    }
```

Import ekle: `import com.example.aicomparator.service.StreamSession;`

- [ ] **Step 6: Testlerin geçtiğini doğrula**

Run: `cd backend && ./mvnw test -Dtest=DebateOrchestratorTests`
Expected: Tests run: 3, Failures: 0, Errors: 0

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/service/DebateOrchestrator.java \
        backend/src/main/java/com/example/aicomparator/controller/DebateController.java \
        backend/src/test/java/com/example/aicomparator/service/DebateOrchestratorTests.java
git commit -m "feat(debate): stop the orchestrator when the client disconnects"
```

---

## Task 4: `AiComparisonService` iptali ve `SseSupport`'un silinmesi

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/service/AiComparisonService.java`
- Modify: `backend/src/main/java/com/example/aicomparator/controller/ChatController.java`
- Delete: `backend/src/main/java/com/example/aicomparator/service/SseSupport.java`
- Test: `backend/src/test/java/com/example/aicomparator/service/AiComparisonServiceTests.java`

- [ ] **Step 1: Başarısız testi yaz**

`AiComparisonServiceTests.java` sonuna ekle. Bu test, sağlayıcı ikinci token'ı yaymadan önce iptal edildiğinde ilk token'ın kaydedildiğini doğrular:

```java
    @Test
    void savesPartialContentWhenClientDisconnects() throws Exception {
        SseEmitter emitter = new SseEmitter(2000L);
        StreamSession session = new StreamSession(emitter);

        AiProvider openAi = mock(AiProvider.class);
        when(openAi.getProviderType()).thenReturn(AiProviderType.OPENAI);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> onToken =
                    invocation.getArgument(2);
            onToken.accept("yarım ");
            session.cancel();
            onToken.accept("kalan");  // StreamCancelledException fırlatır
            return TokenUsage.EMPTY;
        }).when(openAi).streamMessage(any(), any(), any());

        ConversationService conversationService =
                mock(ConversationService.class);
        when(conversationService.startComparison(any()))
                .thenReturn(new ConversationService.UserTurnResult(1L, 2L));
        when(conversationService.saveRetriedResponse(any(), any(), any()))
                .thenReturn(AiResponse.success(
                        3L, "OPENAI", "yarım ", TokenUsage.EMPTY));

        AiComparisonService service = new AiComparisonService(
                List.of(openAi),
                executor,
                conversationService,
                30
        );

        service.streamCompare(null, "Selam", List.of(AiProviderType.OPENAI),
                ResponseIntensity.MEDIUM, session);

        Thread.sleep(300);

        ArgumentCaptor<AiResponse> captor =
                ArgumentCaptor.forClass(AiResponse.class);
        org.mockito.Mockito.verify(conversationService)
                .saveRetriedResponse(eq(1L), eq(2L), captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("yarım ");
    }
```

> **Doğrulandı:** `ConversationService.UserTurnResult` bir record —
> `ConversationService.java:117`, `(Long conversationId, Long userMessageId)` —
> yani yukarıdaki `new` çağrısı derlenir. `AiResponse.success(Long, String,
> String, TokenUsage)` overload'u da mevcut.
>
> Gerekli import'lar (dosyada yoksa ekle): `org.mockito.ArgumentCaptor`, `com.example.aicomparator.dto.ResponseIntensity`.

- [ ] **Step 2: Mevcut testlerin çağrılarını yeni imzaya taşı**

Aynı dosyada:

1. Yedi `new AiComparisonService(...)` çağrısından `new SseSupport(),` satırını **sil**.
2. İki `service.streamCompare(null, "Selam", emitter);` satırını şuna çevir:

```java
        service.streamCompare(null, "Selam", new StreamSession(emitter));
```

- [ ] **Step 3: Testin başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw test -Dtest=AiComparisonServiceTests`
Expected: COMPILATION ERROR — constructor ve `streamCompare` imzaları eşleşmiyor

- [ ] **Step 4: `AiComparisonService`'i güncelle**

**(a)** `SseSupport` alanını ve constructor parametresini sil:

```java
    public AiComparisonService(
            List<AiProvider> providers,
            ExecutorService aiExecutor,
            ConversationService conversationService,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(AiProvider::getProviderType))
                .toList();
        this.aiExecutor = aiExecutor;
        this.conversationService = conversationService;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }
```

`private final SseSupport sseSupport;` alanını sil.

**(b)** Üç `streamCompare` overload'unda `SseEmitter emitter` → `StreamSession session`:

```java
    public void streamCompare(
            Long conversationId,
            String userMessage,
            StreamSession session
    ) {
        streamCompare(conversationId, userMessage, null,
                ResponseIntensity.MEDIUM, session);
    }

    public void streamCompare(
            Long conversationId,
            String userMessage,
            List<AiProviderType> requestedProviderTypes,
            StreamSession session
    ) {
        streamCompare(conversationId, userMessage, requestedProviderTypes,
                ResponseIntensity.MEDIUM, session);
    }

    public void streamCompare(
            Long conversationId,
            String userMessage,
            List<AiProviderType> requestedProviderTypes,
            ResponseIntensity intensity,
            StreamSession session
    ) {
        ResponseIntensity effectiveIntensity =
                ResponseIntensity.orDefault(intensity);
        List<AiProvider> selectedProviders = selectProviders(
                requestedProviderTypes
        );

        ConversationService.UserTurnResult turn = conversationId == null
                ? conversationService.startComparison(userMessage)
                : conversationService.startContinuation(
                        conversationId,
                        userMessage
                );

        session.send(
                "start",
                new StreamStartEvent(turn.conversationId(), turn.userMessageId())
        );

        AtomicInteger remaining = new AtomicInteger(selectedProviders.size());

        for (AiProvider provider : selectedProviders) {
            String providerPrompt = conversationId == null
                    ? userMessage
                    : conversationService.buildActiveContextPrompt(
                            turn.conversationId(),
                            userMessage,
                            provider.getProviderType()
                    );

            streamProvider(provider, providerPrompt, turn, effectiveIntensity,
                    session, remaining);
        }
    }
```

Emitter callback kayıtları (`emitter.onTimeout`, `emitter.onError`) silinir — `StreamSession` constructor'ı yapıyor.

**(c)** `streamProvider` — iptal kontrolü ve kısmi kayıt:

```java
    private void streamProvider(
            AiProvider provider,
            String prompt,
            ConversationService.UserTurnResult turn,
            ResponseIntensity intensity,
            StreamSession session,
            AtomicInteger remaining
    ) {
        String providerName = provider.getProviderType().name();
        StringBuilder accumulated = new StringBuilder();

        CompletableFuture
                .supplyAsync(
                        () -> provider.streamMessage(prompt, intensity, delta -> {
                            session.abortIfCancelled();
                            accumulated.append(delta);
                            session.send(
                                    "token",
                                    new StreamTokenEvent(providerName, delta)
                            );
                        }),
                        aiExecutor
                )
                .orTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((usage, throwable) -> {
                    Throwable cause = throwable instanceof CompletionException
                            ? throwable.getCause()
                            : throwable;

                    if (cause instanceof StreamCancelledException) {
                        String partial = accumulated.toString();
                        if (!partial.isBlank()) {
                            // İstemci gitti ama token'lar ödendi: sakla.
                            conversationService.saveRetriedResponse(
                                    turn.conversationId(),
                                    turn.userMessageId(),
                                    AiResponse.success(null, providerName,
                                            partial, TokenUsage.EMPTY)
                            );
                        }
                    } else if (throwable == null) {
                        String content = accumulated.toString();

                        if (content.isBlank()) {
                            log.warn(
                                    "{} sağlayıcısı streaming sırasında boş cevap döndürdü.",
                                    providerName
                            );

                            session.send(
                                    "error",
                                    new StreamErrorEvent(
                                            providerName,
                                            "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin."
                                    )
                            );
                        } else {
                            TokenUsage resolvedUsage =
                                    usage == null ? TokenUsage.EMPTY : usage;
                            AiResponse saved = conversationService.saveRetriedResponse(
                                    turn.conversationId(),
                                    turn.userMessageId(),
                                    AiResponse.success(null, providerName,
                                            content, resolvedUsage)
                            );

                            session.send(
                                    "done",
                                    new StreamDoneEvent(
                                            providerName,
                                            saved.messageId(),
                                            saved.content(),
                                            saved.usage()
                                    )
                            );
                        }
                    } else {
                        String errorMessage = cause instanceof TimeoutException
                                ? "Yanıt zaman aşımına uğradı. Tekrar deneyin."
                                : "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin.";

                        log.warn(
                                "{} sağlayıcısından streaming yanıtı alınamadı: {}",
                                providerName,
                                cause.getMessage(),
                                cause
                        );

                        session.send(
                                "error",
                                new StreamErrorEvent(providerName, errorMessage)
                        );
                    }

                    if (remaining.decrementAndGet() == 0) {
                        session.complete();
                    }
                });
    }
```

`SseEmitter` import'unu sil.

- [ ] **Step 5: `ChatController`'ı güncelle**

```java
    @PostMapping(
            value = "/compare/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter compareStream(
            @Valid @RequestBody ChatRequest request
    ) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);

        aiComparisonService.streamCompare(
                request.conversationId(),
                request.message().trim(),
                request.providers(),
                request.intensity(),
                new StreamSession(emitter)
        );

        return emitter;
    }
```

Import ekle: `import com.example.aicomparator.service.StreamSession;`

- [ ] **Step 6: `SseSupport`'u sil**

```bash
git rm backend/src/main/java/com/example/aicomparator/service/SseSupport.java
```

- [ ] **Step 7: Tüm backend testlerinin geçtiğini doğrula**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS, Failures: 0, Errors: 0

> MySQL çalışıyor olmalı ve `AI_COMPARATOR_DB_PASSWORD`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` export edilmiş olmalı.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/service/AiComparisonService.java \
        backend/src/main/java/com/example/aicomparator/controller/ChatController.java \
        backend/src/test/java/com/example/aicomparator/service/AiComparisonServiceTests.java
git commit -m "feat(compare): cancel streams on disconnect, drop SseSupport"
```

---

## Task 5: Münazara rate limit'i

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/config/AiRateLimitFilter.java`
- Test: `backend/src/test/java/com/example/aicomparator/config/AiRateLimitFilterTests.java`

- [ ] **Step 1: Başarısız testleri yaz**

`AiRateLimitFilterTests.java`'ya ekle:

```java
    @Test
    void shouldRejectDebateRequestsBeyondDebateCapacity() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(20, 3, 2, 60);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(
                    debateRequest(),
                    new MockHttpServletResponse(),
                    new MockFilterChain()
            );
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();

        filter.doFilter(debateRequest(), blockedResponse, blockedChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedChain.getRequest()).isNull();
    }

    @Test
    void shouldNotLimitDebateReads() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(1, 60, 1, 60);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    "/api/debates"
            );
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void shouldKeepChatAndDebateBucketsSeparate() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(1, 60, 1, 60);

        filter.doFilter(chatRequest(), new MockHttpServletResponse(),
                new MockFilterChain());

        // Sohbet kovası tükendi ama münazara kovası dolu olmalı.
        MockHttpServletResponse debateResponse = new MockHttpServletResponse();
        MockFilterChain debateChain = new MockFilterChain();
        filter.doFilter(debateRequest(), debateResponse, debateChain);

        assertThat(debateChain.getRequest()).isNotNull();
        assertThat(debateResponse.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest debateRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/debates/stream"
        );
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
```

- [ ] **Step 2: Mevcut testlerin constructor çağrılarını güncelle**

Üç mevcut testte `new AiRateLimitFilter(2, 60)` → `new AiRateLimitFilter(2, 60, 5, 30)` ve `new AiRateLimitFilter(1, 60)` → `new AiRateLimitFilter(1, 60, 5, 30)`.

- [ ] **Step 3: Testlerin başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw test -Dtest=AiRateLimitFilterTests`
Expected: COMPILATION ERROR — dört parametreli constructor yok

- [ ] **Step 4: Filtreyi güncelle**

`AiRateLimitFilter.java`'nın sınıf gövdesinin üst kısmını (alanlar, constructor, `doFilter`) şununla değiştir; `TokenBucket` iç sınıfı **aynen kalır**:

```java
/**
 * IP başına token-bucket rate limiter. Yalnızca ücretli AI çağrılarını
 * tetikleyen POST uçlarını korur: /api/chat/** ve /api/debates/**.
 *
 * <p>Münazaranın kendi kovası vardır ve daha sıkıdır: tek bir münazara isteği
 * (katılımcı × tur + 1) kadar sağlayıcı çağrısı üretir, yani sohbet
 * isteğinden bir büyüklük mertebesi daha pahalıdır.
 *
 * <p>GET/DELETE sınırlanmaz — geçmiş listeleme ve münazara açma tek bir AI
 * çağrısı bile yapmaz.
 */
@Component
public class AiRateLimitFilter extends HttpFilter {

    private final int chatCapacity;
    private final long chatRefillIntervalMillis;
    private final int debateCapacity;
    private final long debateRefillIntervalMillis;

    private final ConcurrentHashMap<String, TokenBucket> chatBuckets =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> debateBuckets =
            new ConcurrentHashMap<>();

    public AiRateLimitFilter(
            @Value("${ai.rate-limit.capacity:20}") int chatCapacity,
            @Value("${ai.rate-limit.refill-interval-seconds:3}")
            long chatRefillIntervalSeconds,
            @Value("${ai.rate-limit.debate.capacity:5}") int debateCapacity,
            @Value("${ai.rate-limit.debate.refill-interval-seconds:30}")
            long debateRefillIntervalSeconds
    ) {
        this.chatCapacity = chatCapacity;
        this.chatRefillIntervalMillis = chatRefillIntervalSeconds * 1000;
        this.debateCapacity = debateCapacity;
        this.debateRefillIntervalMillis = debateRefillIntervalSeconds * 1000;
    }

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        String uri = request.getRequestURI();
        boolean isPost = "POST".equals(request.getMethod());

        ConcurrentHashMap<String, TokenBucket> buckets;
        int capacity;
        long refillIntervalMillis;

        if (isPost && uri.startsWith("/api/chat/")) {
            buckets = chatBuckets;
            capacity = chatCapacity;
            refillIntervalMillis = chatRefillIntervalMillis;
        } else if (isPost && uri.startsWith("/api/debates/")) {
            buckets = debateBuckets;
            capacity = debateCapacity;
            refillIntervalMillis = debateRefillIntervalMillis;
        } else {
            chain.doFilter(request, response);
            return;
        }

        int bucketCapacity = capacity;
        TokenBucket bucket = buckets.computeIfAbsent(
                clientKey(request),
                key -> new TokenBucket(bucketCapacity)
        );

        if (!bucket.tryConsume(capacity, refillIntervalMillis)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Çok fazla istek gönderildi. "
                            + "Lütfen birazdan tekrar deneyin.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }
```

- [ ] **Step 5: Testlerin geçtiğini doğrula**

Run: `cd backend && ./mvnw test -Dtest=AiRateLimitFilterTests`
Expected: Tests run: 6, Failures: 0, Errors: 0

- [ ] **Step 6: `application.properties`'e ayarları ekle**

`resources/application.properties` içinde 17. satırdan sonra:

```properties
ai.rate-limit.debate.capacity=${AI_RATE_LIMIT_DEBATE_CAPACITY:5}
ai.rate-limit.debate.refill-interval-seconds=${AI_RATE_LIMIT_DEBATE_REFILL_INTERVAL_SECONDS:30}
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/config/AiRateLimitFilter.java \
        backend/src/test/java/com/example/aicomparator/config/AiRateLimitFilterTests.java \
        backend/src/main/resources/application.properties
git commit -m "feat(rate-limit): guard debate endpoint with its own stricter bucket"
```

---

## Task 6: `api.js` iptal sinyali

Bu görevin otomatik testi yoktur: `vite.config.js` `environment: 'node'` kullanıyor ve `api.js` `window.setTimeout` çağırıyor. jsdom kurulumu Paket 2'nin işi. Doğrulama Task 10'daki elle kontrol listesiyle yapılır.

**Files:**
- Modify: `frontend/src/services/api.js`

- [ ] **Step 1: `streamCompareMessage`'a `signal` ekle**

İmzayı ve iptal kurulumunu değiştir:

```js
export async function streamCompareMessage(
  message,
  conversationId,
  providers,
  intensity,
  handlers,
  signal,
) {
  const controller = new AbortController()
  let idleTimeoutId = null
  let cancelledByUser = false

  const handleExternalAbort = () => {
    cancelledByUser = true
    controller.abort()
  }

  if (signal?.aborted) {
    handleExternalAbort()
  } else {
    signal?.addEventListener('abort', handleExternalAbort)
  }

  const resetIdleTimeout = () => {
    window.clearTimeout(idleTimeoutId)
    idleTimeoutId = window.setTimeout(
      () => controller.abort(),
      STREAM_IDLE_TIMEOUT_MS,
    )
  }

  resetIdleTimeout()
```

- [ ] **Step 2: `catch` ve `finally` bloklarını güncelle**

Aynı fonksiyonun sonundaki `catch`/`finally`:

```js
  } catch (error) {
    if (error.name === 'AbortError') {
      // Kullanıcı kendi durdurduysa bu bir hata değil: sessizce bit.
      if (cancelledByUser) {
        return
      }

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
    signal?.removeEventListener('abort', handleExternalAbort)
  }
```

- [ ] **Step 3: `startDebateStream` için aynısını yap**

İmza:

```js
export async function startDebateStream(debateRequest, handlers, signal) {
```

Fonksiyon gövdesinin başına Step 1'deki `cancelledByUser` / `handleExternalAbort` bloğunu, sonuna Step 2'deki `catch`/`finally` bloğunu birebir aynı şekilde uygula.

- [ ] **Step 4: Lint ve build'in geçtiğini doğrula**

Run: `cd frontend && npm run lint && npm run build`
Expected: eslint hatasız, `vite build` "built in ..." ile biter

- [ ] **Step 5: Commit**

```bash
git add frontend/src/services/api.js
git commit -m "feat(api): accept an external AbortSignal on both streams"
```

---

## Task 7: Karşılaştırma modunda Durdur butonu

**Files:**
- Modify: `frontend/src/components/ChatInput.jsx`
- Modify: `frontend/src/components/CompareView.jsx`
- Modify: `frontend/src/components/AiPanel.jsx`
- Modify: `frontend/src/App.css`

- [ ] **Step 1: `ChatInput`'a `onStop` ekle**

İmzaya `onStop` ekle:

```js
function ChatInput({
  onSend,
  onStop,
  disabled = false,
  isLoading = false,
  providerCount = 3,
}) {
```

`chat-input__footer` içindeki butonu şununla değiştir:

```jsx
        {isLoading ? (
          <button
            type="button"
            className="chat-input__stop-button"
            onClick={onStop}
          >
            <span>⏹ Durdur</span>
          </button>
        ) : (
          <button type="submit" disabled={cannotSend}>
            <span>Gönder</span>
            <span aria-hidden="true">↗</span>
          </button>
        )}
```

- [ ] **Step 2: `CompareView`'a `AbortController` ref'i ekle**

`useState` import'unu genişlet:

```js
import { useEffect, useRef, useState } from 'react'
```

`historyCollapsed` state'inin altına:

```js
  const abortControllerRef = useRef(null)
```

- [ ] **Step 3: `handleSend`'i controller ile bağla**

`handleSend` içinde `setIsLoading(true)` satırından hemen sonra:

```js
    const controller = new AbortController()
    abortControllerRef.current = controller
```

`streamCompareMessage` çağrısına altıncı argüman olarak `controller.signal` ekle — handlers nesnesini kapatan `},` ile `)` arasına:

```js
        },
        controller.signal,
      )
```

`finally` bloğunu güncelle:

```js
    } finally {
      abortControllerRef.current = null
      setIsLoading(false)
    }
```

- [ ] **Step 4: `handleStop`'u ekle ve `ChatInput`'a geçir**

`handleSend`'in hemen altına:

```js
  function handleStop() {
    abortControllerRef.current?.abort()
    setResponses((current) =>
      current.map((response) =>
        response.streaming
          ? { ...response, streaming: false, stopped: true }
          : response,
      ),
    )
  }
```

`ChatInput` kullanımına prop ekle:

```jsx
        <ChatInput
          onSend={handleSend}
          onStop={handleStop}
          disabled={inputDisabled}
          isLoading={isLoading}
          providerCount={selectedProviders.length}
        />
```

- [ ] **Step 5: `AiPanel`'e `Durduruldu` statüsü ekle**

`statusLabel` zincirinde, `panelError` dalının hemen ardına yeni bir dal ekle:

```js
  } else if (panelError) {
    statusLabel = 'Hata'
    statusState = 'error'
  } else if (response?.stopped) {
    statusLabel = 'Durduruldu'
    statusState = 'stopped'
  } else if (content) {
```

- [ ] **Step 6: CSS ekle**

`App.css` içinde `.ai-panel__status--error .ai-panel__status-dot { ... }` bloğunun (yaklaşık 839. satır) hemen ardına:

```css
.ai-panel__status--stopped {
  color: #cbd5e1;
}

.ai-panel__status--stopped .ai-panel__status-dot {
  background: #94a3b8;
}

.chat-input__stop-button {
  background: #7f1d1d;
  border-color: #b91c1c;
}

.chat-input__stop-button:hover {
  background: #991b1b;
}
```

- [ ] **Step 7: Lint ve build'in geçtiğini doğrula**

Run: `cd frontend && npm run lint && npm run build`
Expected: eslint hatasız, build başarılı

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/ChatInput.jsx \
        frontend/src/components/CompareView.jsx \
        frontend/src/components/AiPanel.jsx \
        frontend/src/App.css
git commit -m "feat(compare): turn the send button into a stop button while streaming"
```

---

## Task 8: Münazara modunda Durdur butonu

**Files:**
- Modify: `frontend/src/components/DebateView.jsx`
- Modify: `frontend/src/components/DebateTranscript.jsx`
- Modify: `frontend/src/components/DebateHistory.jsx`
- Modify: `frontend/src/App.css`

- [ ] **Step 1: `DebateView`'a `AbortController` ref'i ekle**

Import'u genişlet:

```js
import { useEffect, useRef, useState } from 'react'
```

`historyCollapsed` state'inin altına:

```js
  const abortControllerRef = useRef(null)
```

- [ ] **Step 2: `handleStart`'ı controller ile bağla**

`setIsRunning(true)` satırından hemen sonra:

```js
    const controller = new AbortController()
    abortControllerRef.current = controller
```

`startDebateStream` çağrısına üçüncü argüman olarak `controller.signal` ekle — handlers nesnesini kapatan `}` ile `)` arasına:

```js
        },
        controller.signal,
      )
```

`try` bloğunun sonuna, `catch`'ten hemen önce `finally` yoktur; `catch` bloğunun ardına ekle:

```js
    } finally {
      abortControllerRef.current = null
    }
```

- [ ] **Step 3: `handleStop`'u ekle**

`handleStart`'ın hemen altına:

```js
  function handleStop() {
    abortControllerRef.current?.abort()
    setRounds((current) =>
      current.map((round) => ({
        ...round,
        entries: round.entries.map((entry) =>
          entry.streaming
            ? { ...entry, streaming: false, stopped: true }
            : entry,
        ),
      })),
    )
    setSynthesis((current) =>
      current?.streaming
        ? { ...current, streaming: false, stopped: true }
        : current,
    )
    setIsRunning(false)
    refreshHistory()
  }
```

- [ ] **Step 4: Başlığa Durdur butonunu koy**

`DebateView`'daki `app__header` içinde, kapanış `</div>`'inden sonra ve `</header>`'dan önce:

```jsx
          {isRunning && (
            <button
              type="button"
              className="debate-stop-button"
              onClick={handleStop}
            >
              ⏹ Durdur
            </button>
          )}
```

- [ ] **Step 5: `DebateTranscript`'te durduruldu statüsünü göster**

`columnStatus` fonksiyonunu güncelle:

```js
function columnStatus(turns) {
  if (turns.some((turn) => turn.entry.streaming)) {
    return { className: 'debate-entry__status--streaming', label: 'Yazıyor…' }
  }
  if (turns.some((turn) => turn.entry.error)) {
    return { className: 'debate-entry__status--error', label: 'Hata' }
  }
  if (turns.some((turn) => turn.entry.stopped)) {
    return { className: 'debate-entry__status--stopped', label: 'Durduruldu' }
  }
  return { className: 'debate-entry__status--done', label: 'Tamamlandı' }
}
```

Sentez başlığındaki statü metnini de güncelle:

```jsx
            <span className="debate-entry__status">
              {synthesis.streaming
                ? 'Yazılıyor…'
                : synthesis.stopped
                  ? 'Durduruldu'
                  : 'Tamamlandı'}
            </span>
```

- [ ] **Step 6: `DebateHistory`'de `CANCELLED` rozetini göster**

Geçmişte iptal edilen münazaranın görünür olması spec gereği. `conversation-item__date` span'ını şununla değiştir:

```jsx
                <span className="conversation-item__date">
                  {debate.rounds} tur · {formatDate(debate.updatedAt)}
                  {debate.status === 'CANCELLED' && (
                    <span className="debate-status-badge"> · durduruldu</span>
                  )}
                  {debate.status === 'FAILED' && (
                    <span className="debate-status-badge"> · başarısız</span>
                  )}
                </span>
```

- [ ] **Step 7: CSS ekle**

`App.css` içinde `.debate-entry__status--error { color: #fecaca; }` satırının (yaklaşık 2125. satır) hemen ardına:

```css
.debate-entry__status--stopped { color: #cbd5e1; }

.debate-stop-button {
  align-self: flex-start;
  padding: 8px 16px;
  background: #7f1d1d;
  border: 1px solid #b91c1c;
  border-radius: 999px;
  color: #fee2e2;
  font-size: 0.8rem;
  cursor: pointer;
}

.debate-stop-button:hover {
  background: #991b1b;
}

.debate-status-badge {
  color: #94a3b8;
}
```

- [ ] **Step 8: Lint, test ve build'in geçtiğini doğrula**

Run: `cd frontend && npm run lint && npm run test && npm run build`
Expected: eslint hatasız, vitest 2 dosya geçer, build başarılı

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/DebateView.jsx \
        frontend/src/components/DebateTranscript.jsx \
        frontend/src/components/DebateHistory.jsx \
        frontend/src/App.css
git commit -m "feat(debate): add a stop button and surface cancelled debates"
```

---

## Task 9: Dokümantasyon

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

- [ ] **Step 1: `CLAUDE.md` "Streaming (SSE)" bölümünü güncelle**

`SseSupport.send` cümlesini şununla değiştir:

```markdown
`StreamSession` her akış için bir kez oluşturulur (controller tarafından) ve üç şeyi taşır: emitter, gönderim kilidi (paralel sağlayıcılar frame'leri iç içe geçirmesin diye) ve iptal bayrağı. İstemci koptuğunda bayrak kalkar; orkestratörler bunu turlar arasında ve token callback'lerinde okur, callback `StreamCancelledException` fırlatarak akan SDK çağrısını keser. Yarım kalan içerik boş değilse yine de kaydedilir.
```

- [ ] **Step 2: `CLAUDE.md` "Rate limiting" bölümünü güncelle**

Bölümün tamamını şununla değiştir:

```markdown
### Rate limiting
`AiRateLimitFilter` in-memory, IP başına token bucket. **Yalnızca POST** istekleri sınırlanır ve iki ayrı kova vardır: `/api/chat/**` (varsayılan 20 / 3sn) ve `/api/debates/**` (varsayılan 5 / 30sn — tek münazara isteği katılımcı × tur + 1 kadar sağlayıcı çağrısı üretir). GET/DELETE hiç sınırlanmaz, yoksa geçmişten münazara açmak 429 yerdi. Harici bağımlılık yok.
```

- [ ] **Step 3: `README.md` özellik listesine iki madde ekle**

"Münazaraların MySQL'de saklanıp geçmişten yeniden açılması" satırının altına:

```markdown
- Akan cevabı durdurma; istemci koptuğunda backend'in de sağlayıcı çağrılarını kesmesi
- Münazara ucu için ayrı ve daha sıkı rate limit
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: describe stream cancellation and the debate rate limit"
```

---

## Task 10: Uçtan uca doğrulama

Otomatik testler "para yanmıyor"u ispatlamaz — asıl kanıt buradadır.

**Files:** (kod değişikliği yok)

- [ ] **Step 1: Tüm test paketini çalıştır**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS, Failures: 0, Errors: 0

Run: `cd frontend && npm run lint && npm run test && npm run build`
Expected: hepsi başarılı

- [ ] **Step 2: Uygulamayı ayağa kaldır**

```bash
cd backend && ./mvnw spring-boot:run
```

Ayrı bir kabukta:

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Münazarayı Durdur butonuyla kes**

3 katılımcı × 3 tur bir münazara başlat. 1. tur akarken **⏹ Durdur**'a bas.

Expected: backend logunda 2. tura ait hiçbir sağlayıcı çağrısı yok; `Münazara yürütülürken hata` uyarısı yok; sütunlarda "Durduruldu" yazıyor ve o ana kadar akan metin duruyor.

- [ ] **Step 4: Sekmeyi kapatarak kes (asıl senaryo)**

Yeni bir münazara başlat, 1. tur akarken tarayıcı sekmesini kapat.

Expected: Step 3 ile aynı — backend 2. tura geçmiyor. Durdur butonu bu davranışın yalnızca görünen yüzü; asıl düzeltme budur.

- [ ] **Step 5: İptal edilen münazarayı geçmişten aç**

Expected: sol listede "· durduruldu" rozetiyle görünüyor; açılınca 1. turun cevapları duruyor.

- [ ] **Step 6: Münazara rate limit'ini tetikle**

Arka arkaya 6 münazara başlat (her birini hemen durdurabilirsin).

Expected: 6.'sında 429 ve "Çok fazla istek gönderildi." mesajı.

- [ ] **Step 7: Okuma uçlarının sınırlanmadığını doğrula**

Geçmişten 10 münazara aç.

Expected: hiçbirinde 429 yok.

- [ ] **Step 8: Karşılaştırma modunda durdur**

3 AI ile bir mesaj gönder, akarken **⏹ Durdur**'a bas.

Expected: panellerde "Durduruldu" yazıyor, kısmi cevaplar ekranda kalıyor, **hata mesajı çıkmıyor** (özellikle "İstek zaman aşımına uğradı" görünmemeli).

- [ ] **Step 9: Sonuçları raporla**

Her adımın gerçek çıktısını yaz. Bir adım beklenenden farklı davrandıysa düzeltmeden önce raporla.

---

## Self-Review Notları

**Spec kapsamı:** Spec'in üç tasarım bölümü ve doğrulama bölümü karşılandı — Bölüm 1 → Task 1-4, Bölüm 2 → Task 5, Bölüm 3 → Task 6-8, Doğrulama → Task 10. Spec'te açıkça yazmayan ama gereğinden çıkan iki ek: `DebateHistory`'de `CANCELLED` rozeti (spec "geçmişte görünür" diyor ama bileşen bugün hiç status render etmiyor) ve `CLAUDE.md`/`README.md` güncellemeleri (Task 9).

**İmza tutarlılığı:** `runDebate(DebateRequest, StreamSession)`, `streamCompare(..., StreamSession)`, `session.send(String, Object)`, `session.abortIfCancelled()`, `session.complete()`, `session.isCancelled()`, `session.cancel()`, `debateService.markCancelled(Long)`, `new AiRateLimitFilter(int, long, int, long)` — plan boyunca aynı.

**Doğrulanan varsayımlar:** `ConversationService.UserTurnResult` record (`ConversationService.java:117`); `AiResponse.success(Long, String, String, TokenUsage)` mevcut; `DebateStatus` VARCHAR olarak saklanıyor (`Debate.java`, `@Enumerated(EnumType.STRING)`) — `CANCELLED` için migration gerekmiyor; `AiRateLimitFilterTests` bugün iki argümanlı constructor kullanıyor, Task 5 Step 2 bunları güncelliyor.
