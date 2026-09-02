# Prompt Caching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Karşılaştırma modunun büyüyen prompt prefix'ini cache'lenebilir hale getirmek ve cache'in gerçekten çalıştığını ölçülebilir kılmak.

**Architecture:** Prompt, `PromptParts(cacheablePrefix, volatileSuffix)` değer tipiyle ikiye ayrılır ve `AiProvider` arayüzü düz `String` yerine bunu alır. Intensity yönergesi prompt'un başından değişken kısmın başına taşınır — pozisyon 0'daki değişiklik tüm prefix'i geçersiz kıldığı için asıl düzeltme budur. Anthropic prefix'in sonuna açık bir `cache_control` breakpoint koyar; OpenAI ve Gemini otomatik prefix cache'ine bırakılır. Üç sağlayıcının da cache token'ları `TokenUsage`'a okunup DB'ye yazılır.

**Tech Stack:** Spring Boot 4 / Java 21, anthropic-java / openai-java / google-genai SDK'ları, Flyway, JUnit 5 + AssertJ + Mockito, React 19.

**Spec:** `docs/superpowers/specs/2026-09-02-prompt-caching-design.md`

---

## Bağlam: iki uyarı

**Sıra önemli: önce akış iptali işi bitmeli.** `feat/stream-cancellation`
branch'inde o iş yarım kaldı (Task 3-10 yapılmadı). Bu planın Task 3'ü ile o
planın Task 3-4'ü **aynı iki dosyayı** ağır biçimde değiştiriyor —
`AiComparisonService` ve `DebateOrchestrator`. İkisini paralel branch'lerde
yürütmek kaçınılmaz ve zahmetli bir merge çakışması üretir.

Doğru sıra: akış iptali paketini bitir, `main`'e al, sonra bu planı onun
üstünden başlat. Bu spec ve plan `feat/stream-cancellation` üzerinde
commit'lendi, dolayısıyla o branch merge edildiğinde `main`'e kendiliğinden
gelir.

**Münazara modu kapsam dışı.** `DebatePromptBuilder.buildCritiqueRoundPrompt` yalnızca bir önceki turu gönderiyor, tam transcript'i değil — yani büyüyen prefix yok, cache'lenecek bir şey yok. Münazara `PromptParts.volatileOnly(...)` ile geçer. Bunu "eksik" sanıp düzeltmeye çalışma.

---

## Dosya Yapısı

**Yeni:**
- `backend/src/main/java/com/example/aicomparator/dto/PromptParts.java` — prompt'un stabil/değişken ayrımı
- `backend/src/main/resources/db/migration/V5__add_cache_token_usage.sql`
- `backend/src/test/java/com/example/aicomparator/dto/PromptPartsTests.java`

**Değişen (backend):**
- `dto/ResponseIntensity.java` — yönerge artık değişken kısmın başına
- `dto/TokenUsage.java` — iki yeni alan, iki argümanlı constructor korunur
- `ai/AiProvider.java` + üç uygulama — `String` yerine `PromptParts`
- `service/ConversationService.java` — `buildActiveContextPrompt` ve `buildPromptForUserMessage` `PromptParts` döndürür
- `service/AiComparisonService.java`, `service/DebateOrchestrator.java` — çağrı noktaları
- `entity/Message.java`, `entity/DebateMessage.java` — iki kolon
- Testler: `ConversationServiceIntegrationTests`, `AiComparisonServiceTests`, `DebateOrchestratorTests`

**Değişen (frontend):**
- `src/components/TokenUsageBadge.jsx` — cache okuması gösterimi

---

## Task 1: `PromptParts` ve intensity'nin yeri

**Files:**
- Create: `backend/src/main/java/com/example/aicomparator/dto/PromptParts.java`
- Create: `backend/src/test/java/com/example/aicomparator/dto/PromptPartsTests.java`
- Modify: `backend/src/main/java/com/example/aicomparator/dto/ResponseIntensity.java`

- [ ] **Step 1: Başarısız testi yaz**

`PromptPartsTests.java`:

```java
package com.example.aicomparator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptPartsTests {

    @Test
    void joinsPrefixAndSuffix() {
        PromptParts parts = new PromptParts("STABIL\n", "DEGISKEN");

        assertThat(parts.joined()).isEqualTo("STABIL\nDEGISKEN");
        assertThat(parts.hasCacheablePrefix()).isTrue();
    }

    @Test
    void volatileOnlyHasNoCacheablePrefix() {
        PromptParts parts = PromptParts.volatileOnly("hepsi degisken");

        assertThat(parts.cacheablePrefix()).isEmpty();
        assertThat(parts.hasCacheablePrefix()).isFalse();
        assertThat(parts.joined()).isEqualTo("hepsi degisken");
    }

    @Test
    void blankPrefixIsNotCacheable() {
        PromptParts parts = new PromptParts("   ", "soru");

        assertThat(parts.hasCacheablePrefix()).isFalse();
    }

    @Test
    void intensityDirectiveGoesIntoTheVolatilePart() {
        String suffix = ResponseIntensity.LOW.applyTo("USER: soru\n\nASSISTANT:");

        assertThat(suffix).startsWith("Kısa ve öz");
        assertThat(suffix).endsWith("USER: soru\n\nASSISTANT:");
    }

    @Test
    void mediumIntensityAddsNothing() {
        String suffix = ResponseIntensity.MEDIUM.applyTo("USER: soru");

        assertThat(suffix).isEqualTo("USER: soru");
    }
}
```

- [ ] **Step 2: Testin başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw test -Dtest=PromptPartsTests`
Expected: COMPILATION ERROR — `PromptParts` sınıfı yok

- [ ] **Step 3: `PromptParts`'ı yaz**

```java
package com.example.aicomparator.dto;

/**
 * Bir prompt'un cache açısından iki parçası.
 *
 * <p>Prompt caching bir prefix eşleşmesidir: prefix'in herhangi bir yerindeki
 * tek bir byte değişikliği o noktadan sonrasının tamamını geçersiz kılar. Bu
 * yüzden stabil kısım fiziksel olarak değişken kısımdan önce gelmeli ve
 * sağlayıcıya hangisinin hangisi olduğu söylenebilmelidir.
 *
 * @param cacheablePrefix istekler arasında byte-byte aynı kalan kısım
 * @param volatileSuffix  her istekte değişen kısım
 */
public record PromptParts(String cacheablePrefix, String volatileSuffix) {

    /** Cache'lenecek stabil kısmı olmayan prompt (ör. münazara turları). */
    public static PromptParts volatileOnly(String whole) {
        return new PromptParts("", whole);
    }

    public boolean hasCacheablePrefix() {
        return !cacheablePrefix.isBlank();
    }

    public String joined() {
        return cacheablePrefix.isEmpty()
                ? volatileSuffix
                : cacheablePrefix + volatileSuffix;
    }
}
```

- [ ] **Step 4: `ResponseIntensity`'nin Javadoc'unu düzelt**

Davranış aynı kalır (yönerge metnin başına eklenir) ama **anlamı** değişir:
artık tüm prompt'un değil, değişken kısmın başına ekleniyor. Yalnızca
`applyTo`'nun Javadoc'unu değiştir:

```java
    /**
     * Yoğunluk yönergesini prompt'un <b>değişken</b> kısmının başına ekler.
     *
     * <p>Bu metin asla cache'lenebilir prefix'e girmemeli: prefix'in başındaki
     * bir değişiklik tüm cache'i geçersiz kılar, ve yoğunluk istek başına
     * değişebilir.
     */
    public String applyTo(String volatilePart) {
```

Parametre adını `userMessage` → `volatilePart` olarak değiştir. Gövde aynı.

- [ ] **Step 5: Testin geçtiğini doğrula**

Run: `cd backend && ./mvnw test -Dtest=PromptPartsTests`
Expected: Tests run: 5, Failures: 0, Errors: 0

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/dto/PromptParts.java \
        backend/src/main/java/com/example/aicomparator/dto/ResponseIntensity.java \
        backend/src/test/java/com/example/aicomparator/dto/PromptPartsTests.java
git commit -m "feat(cache): add PromptParts to separate stable and volatile prompt halves"
```

---

## Task 2: `ConversationService` prompt'u ikiye ayırır

Bu görevin testi planın en önemli parçası: caching sessizce bozulur, ve bu test
onu API çağrısı yapmadan yakalar.

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/service/ConversationService.java`
- Test: `backend/src/test/java/com/example/aicomparator/service/ConversationServiceIntegrationTests.java`

- [ ] **Step 1: Başarısız testi yaz**

`ConversationServiceIntegrationTests.java`'ya ekle. Bu sınıf `@SpringBootTest`
ile çalışan bir entegrasyon testidir ve MySQL gerektirir; mevcut testlerin
konuşma kurma biçimini aynen taklit et (dosyadaki mevcut testlere bak —
`startComparison` / `saveRetriedResponse` / `selectActiveMessage` çağrılarıyla
bir dal kurup ilerletiyorlar).

```java
    @Test
    void cacheablePrefixGrowsAsAStrictPrefixAcrossTurns() {
        // 1. tur: konuşmayı başlat ve bir cevabı aktif dal olarak seç.
        ConversationService.UserTurnResult firstTurn =
                conversationService.startComparison("Java nedir?");
        AiResponse firstAnswer = conversationService.saveRetriedResponse(
                firstTurn.conversationId(),
                firstTurn.userMessageId(),
                AiResponse.success(null, "ANTHROPIC", "Java bir dildir.",
                        TokenUsage.EMPTY));
        conversationService.selectActiveMessage(
                firstTurn.conversationId(), firstAnswer.messageId());

        PromptParts turnTwo = conversationService.buildActiveContextPrompt(
                firstTurn.conversationId(), "Örnek verir misin?",
                AiProviderType.ANTHROPIC);

        // 2. tur: aynı dalı bir mesaj daha ilerlet.
        ConversationService.UserTurnResult secondTurn =
                conversationService.startContinuation(
                        firstTurn.conversationId(), "Örnek verir misin?");
        AiResponse secondAnswer = conversationService.saveRetriedResponse(
                secondTurn.conversationId(),
                secondTurn.userMessageId(),
                AiResponse.success(null, "ANTHROPIC", "Şöyle: ...",
                        TokenUsage.EMPTY));
        conversationService.selectActiveMessage(
                secondTurn.conversationId(), secondAnswer.messageId());

        PromptParts turnThree = conversationService.buildActiveContextPrompt(
                firstTurn.conversationId(), "Peki ya performans?",
                AiProviderType.ANTHROPIC);

        // Cache'in çalışmasının tek şartı: önceki prefix, sonrakinin
        // byte-byte öneki olmalı.
        assertThat(turnThree.cacheablePrefix())
                .startsWith(turnTwo.cacheablePrefix());
    }

    @Test
    void cacheablePrefixDoesNotDependOnIntensity() {
        ConversationService.UserTurnResult turn =
                conversationService.startComparison("Java nedir?");
        AiResponse answer = conversationService.saveRetriedResponse(
                turn.conversationId(), turn.userMessageId(),
                AiResponse.success(null, "ANTHROPIC", "Java bir dildir.",
                        TokenUsage.EMPTY));
        conversationService.selectActiveMessage(
                turn.conversationId(), answer.messageId());

        PromptParts parts = conversationService.buildActiveContextPrompt(
                turn.conversationId(), "Örnek?", AiProviderType.ANTHROPIC);

        // Yoğunluk yönergesi prefix'te olmamalı; onu ekleyen taraf
        // volatileSuffix üzerinde çalışır.
        assertThat(parts.cacheablePrefix())
                .doesNotContain("Kısa ve öz")
                .doesNotContain("Kapsamlı ve detaylı");
        assertThat(parts.volatileSuffix()).contains("Örnek?");
    }
```

Gerekli import'lar: `com.example.aicomparator.dto.PromptParts`,
`com.example.aicomparator.dto.AiResponse`, `com.example.aicomparator.dto.TokenUsage`,
`com.example.aicomparator.entity.AiProviderType`.

> **Not:** `selectActiveMessage`'ın gerçek adı ve imzası bu sınıfta farklı
> olabilir. Testi yazmadan önce `ConversationService.java`'daki public metot
> listesine bak ve mevcut testlerin bir dalı nasıl ilerlettiğini kopyala.
> Yukarıdaki akış (başlat → cevabı kaydet → aktif seç → devam et) doğru
> sıradır; metot adlarını dosyadan doğrula.

- [ ] **Step 2: Testin başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw test -Dtest=ConversationServiceIntegrationTests`
Expected: COMPILATION ERROR — `buildActiveContextPrompt` `String` döndürüyor, `PromptParts` değil

- [ ] **Step 3: `buildActiveContextPrompt`'u ayır**

Dönüş tipini `PromptParts` yap ve son üç satırı böl:

```java
    public PromptParts buildActiveContextPrompt(
            Long conversationId,
            String newUserContent,
            AiProviderType targetProvider
    ) {
        Conversation conversation = findConversation(conversationId);
        Message currentMessage = conversation.getActiveMessage();

        if (currentMessage == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Devam etmeden önce bir AI cevabı seçilmelidir."
            );
        }

        List<Message> activeBranch = new ArrayList<>();

        while (currentMessage != null) {
            activeBranch.add(currentMessage);
            currentMessage = currentMessage.getParentMessage();
        }

        Collections.reverse(activeBranch);

        // Cache'lenebilir kısım: kimlik + o ana kadarki dal. Her yeni tur
        // bunun sonuna ekler, öncesi byte-byte aynı kalır.
        StringBuilder prefix = new StringBuilder(
                identityPreamble(targetProvider)
        );
        appendTranscript(prefix, activeBranch);

        // Değişken kısım: yeni mesaj. Yoğunluk yönergesi de buraya girer.
        String suffix = "USER: " + newUserContent + "\n\nASSISTANT:";

        return new PromptParts(prefix.toString(), suffix);
    }
```

- [ ] **Step 4: `buildPromptForUserMessage`'ı da ayır**

Bu metot "tekrar dene" yolunda kullanılıyor ve **aynı dalı** yeniden gönderdiği
için cache'ten yararlanır — aynı ayrımı yap. Dosyadaki mevcut gövdeye bak;
`identityPreamble(targetProvider)` ile başlayan kısım prefix, kullanıcı
mesajıyla biten kısım suffix olacak şekilde `PromptParts` döndür.

- [ ] **Step 5: Testin geçtiğini doğrula**

Run: `cd backend && ./mvnw test -Dtest=ConversationServiceIntegrationTests`
Expected: Failures: 0, Errors: 0

> Bu adımda `AiComparisonService` henüz derlenmeyecek (Task 3 onu düzeltiyor).
> Eğer `./mvnw test` derleme hatası verirse, Task 3'ü tamamlayıp sonra iki
> görevin testlerini birlikte çalıştır ve her ikisini de o noktada commit'le.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/service/ConversationService.java \
        backend/src/test/java/com/example/aicomparator/service/ConversationServiceIntegrationTests.java
git commit -m "feat(cache): split the compare prompt into a stable prefix and a volatile tail"
```

---

## Task 3: `AiProvider` arayüzü `PromptParts` alır

Bu görev davranışı **değiştirmez** — üç sağlayıcı da `parts.joined()` kullanır.
Amaç yalnızca bilgiyi sağlayıcıya kadar taşımak. Anthropic'in bunu kullanması
Task 5'te.

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/ai/AiProvider.java`
- Modify: `ai/AnthropicProvider.java`, `ai/OpenAiProvider.java`, `ai/GeminiProvider.java`
- Modify: `service/AiComparisonService.java`, `service/DebateOrchestrator.java`

- [ ] **Step 1: Arayüzü değiştir**

`AiProvider.java` içinde üç metodun `String` parametresini `PromptParts` yap:

```java
    AiResult sendMessage(PromptParts prompt, ResponseIntensity intensity);

    TokenUsage streamMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    );

    default TokenUsage streamSynthesisMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamMessage(prompt, intensity, onToken);
    }
```

`import com.example.aicomparator.dto.PromptParts;` ekle.

- [ ] **Step 2: Üç sağlayıcıyı derlenir hale getir**

Her üçünde de aynı kalıp: parametre tipini `PromptParts prompt` yap ve
`intensity.applyTo(userMessage)` çağrılarını şununla değiştir:

```java
intensity.applyTo(prompt.volatileSuffix())
```

sonra prefix'i başa koy. Yani sağlayıcıya giden nihai metin:

```java
prompt.cacheablePrefix() + intensity.applyTo(prompt.volatileSuffix())
```

Bunu her sağlayıcıda tekrar etmemek için `PromptParts`'a değil — bu **intensity
bilgisi taşıdığı için** `PromptParts`'a ait değil — her sağlayıcının kendi
private yardımcısına koy:

```java
    private String renderPrompt(PromptParts prompt, ResponseIntensity intensity) {
        return prompt.cacheablePrefix()
                + intensity.applyTo(prompt.volatileSuffix());
    }
```

`AnthropicProvider`, `OpenAiProvider` ve `GeminiProvider`'da bu yardımcıyı ekle
ve eski `intensity.applyTo(userMessage)` kullanımlarını `renderPrompt(prompt, intensity)`
ile değiştir. (Anthropic'te bu Task 5'te iki content bloğuna bölünecek.)

- [ ] **Step 3: Çağrı noktalarını güncelle**

`AiComparisonService`:
- `compare(...)` içinde `conversationService.buildActiveContextPrompt(...)` zaten
  `PromptParts` döndürüyor; `requestProvider`'ın parametre tipini `PromptParts`
  yap. `conversationId == null` dalında `PromptParts.volatileOnly(userMessage)` kullan.
- `streamCompare(...)` ve `streamProvider(...)` için aynısı.
- `retryProvider(...)`: `buildPromptForUserMessage` artık `PromptParts` döndürüyor.
- `sendSingle(...)`: `PromptParts.volatileOnly(message)`.

`DebateOrchestrator`:
- `streamParticipant(...)` ve `runSynthesis(...)` içindeki `String prompt`
  parametrelerini `PromptParts`'a çevir; `promptBuilder` çağrılarını
  `PromptParts.volatileOnly(promptBuilder.buildFirstRoundPrompt(...))` biçiminde sar.
  `DebatePromptBuilder`'ın kendisi **değişmez** — `String` döndürmeye devam eder.

- [ ] **Step 4: Testleri derlenir hale getir**

`AiComparisonServiceTests` ve `DebateOrchestratorTests` içindeki Mockito
stub'ları `any()` matcher kullandığı için çoğu değişmeden geçer. Derleme hatası
veren yerlerde `streamMessage(any(), any(), any())` imzası korunur; yalnızca
somut `String` argümanı verilen yerler `PromptParts.volatileOnly("...")` olur.

- [ ] **Step 5: Tüm testlerin geçtiğini doğrula**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS, Failures: 0, Errors: 0

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/ai/ \
        backend/src/main/java/com/example/aicomparator/service/ \
        backend/src/test/java/com/example/aicomparator/service/
git commit -m "refactor(cache): thread PromptParts through the provider interface"
```

---

## Task 4: Cache token'larını ölçmek için `TokenUsage` ve şema

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/dto/TokenUsage.java`
- Create: `backend/src/main/resources/db/migration/V5__add_cache_token_usage.sql`
- Modify: `entity/Message.java`, `entity/DebateMessage.java`
- Modify: `service/ConversationService.java`, `service/DebateService.java`

- [ ] **Step 1: `TokenUsage`'a iki alan ekle**

İki argümanlı constructor korunur, böylece mevcut ~20 çağrı noktası derlenmeye
devam eder:

```java
package com.example.aicomparator.dto;

/**
 * Bir yapay zekâ çağrısının token muhasebesi.
 *
 * <p>Dikkat: prompt caching devredeyken {@code inputTokens} toplam prompt
 * boyutu <b>değildir</b> — yalnızca cache'lenmemiş kalandır. Toplam =
 * inputTokens + cacheReadTokens + cacheWriteTokens.
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
) {

    public static final TokenUsage EMPTY = new TokenUsage(0, 0, 0, 0);

    public TokenUsage(long inputTokens, long outputTokens) {
        this(inputTokens, outputTokens, 0, 0);
    }

    public long totalTokens() {
        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                cacheReadTokens + other.cacheReadTokens,
                cacheWriteTokens + other.cacheWriteTokens
        );
    }
}
```

- [ ] **Step 2: Migration yaz**

`V5__add_cache_token_usage.sql`:

```sql
-- Persist per-message prompt-cache accounting alongside the V4 token columns.
ALTER TABLE messages
    ADD COLUMN cache_read_tokens BIGINT NULL,
    ADD COLUMN cache_write_tokens BIGINT NULL;

ALTER TABLE debate_messages
    ADD COLUMN cache_read_tokens BIGINT NULL,
    ADD COLUMN cache_write_tokens BIGINT NULL;
```

- [ ] **Step 3: Entity'lere kolonları ekle**

`Message.java` ve `DebateMessage.java`'da mevcut `inputTokens` / `outputTokens`
alanlarının hemen altına, aynı kalıpta:

```java
    @Column(name = "cache_read_tokens")
    private Long cacheReadTokens;

    @Column(name = "cache_write_tokens")
    private Long cacheWriteTokens;
```

Her iki sınıfta da constructor'lara ve statik fabrika metotlarına
(`Message`'ın ilgili fabrikası, `DebateMessage.participant` ve
`DebateMessage.synthesis`) iki parametre ekle, alanları ata ve iki getter yaz.
Mevcut `inputTokens` parametresinin geçtiği her yeri örnek al.

- [ ] **Step 4: Kaydetme ve okuma yollarını bağla**

`ConversationService.usageOf(Message)`:

```java
    private static TokenUsage usageOf(Message message) {
        return new TokenUsage(
                message.getInputTokens() == null ? 0 : message.getInputTokens(),
                message.getOutputTokens() == null
                        ? 0 : message.getOutputTokens(),
                message.getCacheReadTokens() == null
                        ? 0 : message.getCacheReadTokens(),
                message.getCacheWriteTokens() == null
                        ? 0 : message.getCacheWriteTokens()
        );
    }
```

`ConversationService`'te mesaj kaydeden yerlerde `usage.cacheReadTokens()` ve
`usage.cacheWriteTokens()` değerlerini de entity'ye geçir. `DebateService`'in
`saveParticipantMessage` / `saveSynthesisMessage` metotlarında ve
`getDebate`'in `new TokenUsage(...)` kurduğu yerde aynısını yap.

- [ ] **Step 5: Testlerin geçtiğini doğrula**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS. Flyway V5'i uygulayacak.

Migration'ın gerçekten uygulandığını doğrula:

```bash
docker exec ai-comparator-mysql-1 mysql --default-character-set=utf8mb4 \
  -u ai_comparator_app ai_comparator \
  -e "SHOW COLUMNS FROM messages LIKE 'cache_%';"
```
Expected: `cache_read_tokens` ve `cache_write_tokens` satırları

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/dto/TokenUsage.java \
        backend/src/main/resources/db/migration/V5__add_cache_token_usage.sql \
        backend/src/main/java/com/example/aicomparator/entity/ \
        backend/src/main/java/com/example/aicomparator/service/
git commit -m "feat(cache): record prompt-cache token accounting"
```

---

## Task 5: Anthropic'te açık `cache_control`

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/ai/AnthropicProvider.java`
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: SDK üye adlarını doğrula (tahmin yürütme)**

```bash
find ~/.m2/repository/com/anthropic -name "anthropic-java-core*.jar" | head -1
```

Çıkan jar için:

```bash
javap -classpath <jar> com.anthropic.models.messages.Usage | grep -i cache
javap -classpath <jar> com.anthropic.models.messages.CacheControlEphemeral | head -20
```

Beklenen: `cacheReadInputTokens()` ve `cacheCreationInputTokens()` (muhtemelen
`Optional<Long>` döner). Gerçek imzayı not al ve aşağıdaki kodu ona göre yaz.

- [ ] **Step 2: `createParams`'ı iki content bloğuna böl**

```java
    private MessageCreateParams createParams(
            PromptParts prompt,
            ResponseIntensity intensity,
            long outputTokenLimit
    ) {
        String suffix = intensity.applyTo(prompt.volatileSuffix());

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(outputTokenLimit);

        if (!prompt.hasCacheablePrefix()) {
            return builder.addUserMessage(suffix).build();
        }

        // Breakpoint stabil kısmın sonunda: sonraki istekler aynı prefix'i
        // okur, değişen kuyruk cache'e yazılmaz. Prefix modelin minimum
        // eşiğinin altındaysa işaretleme sessizce etkisizdir (yazma primi
        // de doğmaz), o yüzden koşul koymuyoruz.
        return builder
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(prompt.cacheablePrefix())
                                .cacheControl(CacheControlEphemeral.builder()
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(suffix)
                                .build())))
                .build();
    }
```

Import'lar: `java.util.List`, `com.anthropic.models.messages.ContentBlockParam`,
`com.anthropic.models.messages.TextBlockParam`,
`com.anthropic.models.messages.CacheControlEphemeral`.

`sendMessage` ve `streamWithLimit` bu metodu `PromptParts` ile çağıracak şekilde
güncellenir; `renderPrompt` yardımcısı Anthropic'ten silinir.

- [ ] **Step 3: Cache token'larını oku**

Bloklu yolda (`sendMessage`), Step 1'de doğruladığın imzayı kullanarak:

```java
        TokenUsage usage = new TokenUsage(
                response.usage().inputTokens(),
                response.usage().outputTokens(),
                response.usage().cacheReadInputTokens().orElse(0L),
                response.usage().cacheCreationInputTokens().orElse(0L));
```

Streaming yolunda (`streamWithLimit`) iki `AtomicLong` daha ekle ve
`event.messageStart()` bloğunda doldur — `start.message().usage()` aynı alanları
taşır:

```java
        AtomicLong cacheRead = new AtomicLong(0);
        AtomicLong cacheWrite = new AtomicLong(0);
        …
                event.messageStart().ifPresent(start -> {
                    inputTokens.set(start.message().usage().inputTokens());
                    cacheRead.set(start.message().usage()
                            .cacheReadInputTokens().orElse(0L));
                    cacheWrite.set(start.message().usage()
                            .cacheCreationInputTokens().orElse(0L));
                });
        …
        return new TokenUsage(inputTokens.get(), outputTokens.get(),
                cacheRead.get(), cacheWrite.get());
```

- [ ] **Step 4: TTL'i yapılandırılabilir bırak (varsayılan 5 dakika)**

`application.properties`'e ekle:

```properties
anthropic.cache.ttl=${ANTHROPIC_CACHE_TTL:5m}
```

`AnthropicProvider` constructor'ına `@Value("${anthropic.cache.ttl:5m}") String cacheTtl`
ekle ve `CacheControlEphemeral.builder()`'a yalnızca `1h` verildiğinde
`.ttl(CacheControlEphemeral.Ttl.TTL_1H)` uygula; aksi halde varsayılanı
(5 dakika) kullan. Ttl enum sabitlerinin gerçek adını Step 1'deki `javap`
çıktısından doğrula.

- [ ] **Step 5: Derleme ve testler**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/ai/AnthropicProvider.java \
        backend/src/main/resources/application.properties
git commit -m "feat(cache): mark the stable prefix with cache_control on Anthropic"
```

---

## Task 6: OpenAI ve Gemini'de cache token'larını oku

Bu iki sağlayıcıda kod cache'i **kurmaz** — otomatik çalışır. Yapılacak tek şey
raporlanan cache token'larını okumak, ki prefix stabilizasyonunun işe yaradığını
görebilelim.

**Files:**
- Modify: `backend/src/main/java/com/example/aicomparator/ai/OpenAiProvider.java`
- Modify: `backend/src/main/java/com/example/aicomparator/ai/GeminiProvider.java`

- [ ] **Step 1: SDK üye adlarını doğrula**

```bash
find ~/.m2/repository/com/openai -name "openai-java-core*.jar" | head -1
javap -classpath <jar> com.openai.models.responses.ResponseUsage | grep -i "cach\|details"
```

```bash
find ~/.m2/repository/com/google/genai -name "*.jar" | head -1
javap -classpath <jar> com.google.genai.types.GenerateContentResponseUsageMetadata | grep -i cach
```

Beklenen: OpenAI'da `inputTokensDetails()` altında bir `cachedTokens()`;
Gemini'de `cachedContentTokenCount()`. Gerçek imzaları not al.

- [ ] **Step 2: OpenAI'da oku**

`sendMessage` içindeki eşlemeyi genişlet (gerçek imzaya göre uyarla):

```java
        TokenUsage usage = response.usage()
                .map(u -> new TokenUsage(
                        u.inputTokens(),
                        u.outputTokens(),
                        u.inputTokensDetails().cachedTokens(),
                        0))
                .orElse(TokenUsage.EMPTY);
```

`streamWithLimit` içindeki `event.completed()` bloğunda aynısını yap.

- [ ] **Step 3: Gemini'de oku**

`extractUsage`'ı genişlet:

```java
    private TokenUsage extractUsage(GenerateContentResponse response) {
        return response.usageMetadata()
                .map(meta -> new TokenUsage(
                        meta.promptTokenCount().orElse(0),
                        meta.candidatesTokenCount().orElse(0),
                        meta.cachedContentTokenCount().orElse(0),
                        0))
                .orElse(TokenUsage.EMPTY);
    }
```

> `extractUsage`'ın çağrıldığı yerdeki `chunkUsage.totalTokens() > 0` koşulu
> `totalTokens()` artık cache alanlarını da topladığı için hâlâ doğru çalışır.

- [ ] **Step 4: Derleme ve testler**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/aicomparator/ai/OpenAiProvider.java \
        backend/src/main/java/com/example/aicomparator/ai/GeminiProvider.java
git commit -m "feat(cache): report cached prompt tokens from OpenAI and Gemini"
```

---

## Task 7: Rozette cache'i göster

**Files:**
- Modify: `frontend/src/components/TokenUsageBadge.jsx`
- Modify: `frontend/src/App.css`

- [ ] **Step 1: Bileşeni oku ve genişlet**

`TokenUsageBadge.jsx`'i oku. `usage.cacheReadTokens > 0` olduğunda ek bir
parça göster — örneğin `· önbellek 1.2k`. Sayı biçimlendirmesi için dosyada
zaten kullanılan yardımcıyı kullan; yeni bir biçimlendirici yazma.

Toplamın anlamı değiştiği için (girdi artık yalnızca cache'lenmemiş kalan),
rozetin başlık/tooltip metni bunu belirtmeli.

- [ ] **Step 2: Lint, test, build**

Run: `cd frontend && npm run lint && npm run test && npm run build`
Expected: hepsi başarılı

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/TokenUsageBadge.jsx frontend/src/App.css
git commit -m "feat(cache): surface cached prompt tokens in the usage badge"
```

---

## Task 8: Dokümantasyon

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

- [ ] **Step 1: `CLAUDE.md`'ye caching bölümü ekle**

"Context building (compare mode)" bölümünün ardına:

```markdown
### Prompt caching
Prompts are split into `PromptParts(cacheablePrefix, volatileSuffix)` and the split is what makes caching work — caching is a prefix match, so a single byte change anywhere in the prefix invalidates everything after it. In compare mode the prefix is the identity preamble plus the active branch transcript; the tail is the intensity directive plus the new user turn. **The intensity directive must stay in the tail** — it used to be prepended to the whole prompt, which invalidated the cache on every intensity change.

`AnthropicProvider` marks the prefix with an explicit `cache_control` breakpoint; OpenAI and Gemini cache automatically once the prefix is stable. Debate mode gets no caching: `buildCritiqueRoundPrompt` sends only the previous round, so there is no growing prefix.

The guard against silent regressions is `ConversationServiceIntegrationTests` asserting that one turn's `cacheablePrefix` is a byte-exact prefix of the next turn's. Caching fails silently — requests keep succeeding, the bill just goes up — so that assertion matters more than it looks.

Note the Anthropic model matters: `claude-haiku-4-5` has a 4096-token minimum cacheable prefix (the highest tier — Opus 5 is 512), so caching only engages after roughly 8-10 turns.
```

- [ ] **Step 2: `CLAUDE.md`'nin münazara iddiasını düzelt**

"The two orchestration services" bölümünde şu cümle **kodla çelişiyor**:

> Round N+1's prompt is built from the full transcript of rounds 1..N

Gerçek: `DebatePromptBuilder.buildCritiqueRoundPrompt` yalnızca
`transcript.get(transcript.size() - 1)` kullanır. Cümleyi düzelt ve tutarsızlığı
not et:

```markdown
- **`DebateOrchestrator`** — **rounds are sequential, participants within a round run in parallel.** Round N+1's prompt carries **only round N's answers**, not the full transcript (`DebatePromptBuilder.buildCritiqueRoundPrompt` reads just the last entry) — so round 3 never sees round 1. Only `buildSynthesisPrompt` gets the whole transcript. Whether that narrow window is intentional is an open question; widening it would also make the debate prompt cacheable.
```

- [ ] **Step 3: `README.md` özellik listesine ekle**

```markdown
- Büyüyen konuşma bağlamı için prompt caching; cache token'larının ölçülüp gösterilmesi
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: describe prompt caching and correct the debate transcript claim"
```

---

## Task 9: Uçtan uca doğrulama

Otomatik testler prefix'in stabil olduğunu kanıtlar; **cache'in gerçekten
tuttuğunu** yalnızca canlı çalıştırma kanıtlar.

- [ ] **Step 1: Tüm testler**

Run: `cd backend && ./mvnw test`
Run: `cd frontend && npm run lint && npm run test && npm run build`
Expected: hepsi yeşil

- [ ] **Step 2: Uygulamayı ayağa kaldır**

```bash
cd backend && ./mvnw spring-boot:run
```

Ayrı kabukta:

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Uzun bir konuşma yürüt**

Yalnızca **Claude**'u seç (tek sağlayıcı, cevap otomatik aktif dal olur) ve
yoğunluğu HIGH yap; böylece dal geçmişi hızlı büyür. Aynı konuşmada arka arkaya
10 mesaj gönder.

- [ ] **Step 4: Cache'in tuttuğunu doğrula**

Her turdan sonra DB'ye bak:

```bash
docker exec ai-comparator-mysql-1 mysql --default-character-set=utf8mb4 \
  -u ai_comparator_app ai_comparator \
  -e "SELECT id, provider, input_tokens, cache_read_tokens, cache_write_tokens FROM messages WHERE provider='ANTHROPIC' ORDER BY id DESC LIMIT 12;"
```

Expected: dal geçmişi 4096 token'ı aştıktan sonraki turlarda `cache_write_tokens`
bir kez dolar, sonraki turlarda `cache_read_tokens > 0` olur ve tur başına büyür.
`input_tokens` ise küçük kalır (yalnızca cache'lenmemiş kuyruk).

Hiçbir turda `cache_read_tokens > 0` görülmüyorsa: iki ardışık isteğin prefix'ini
loglayıp diff'le. İlk farklılık noktası geçersiz kılıcıdır.

- [ ] **Step 5: Yoğunluk değişiminin prefix'i bozmadığını doğrula**

Aynı konuşmada yoğunluğu HIGH → LOW yap ve bir mesaj daha gönder.

Expected: `cache_read_tokens` hâlâ > 0. Bu, intensity düzeltmesinin asıl
sınavıdır — eskiden bu değişiklik tüm cache'i geçersiz kılardı.

- [ ] **Step 6: Diğer iki sağlayıcıyı kontrol et**

Üç sağlayıcıyla yeni bir uzun konuşma yürüt ve OpenAI ile Gemini satırlarında da
`cache_read_tokens` değerinin sıfırdan büyüdüğünü gör. Bunlar otomatik cache
olduğu için eşikleri farklıdır; sıfır kalırsa bunu bir hata değil, gözlem olarak
raporla.

- [ ] **Step 7: Sonuçları raporla**

Her adımın gerçek çıktısını, özellikle Step 4 ve 5'teki SQL sonuçlarını yaz.
Bir adım beklenenden farklı davrandıysa düzeltmeden önce raporla.

---

## Self-Review Notları

**Spec kapsamı:** Spec'in beş tasarım bölümü karşılandı — `PromptParts` → Task 1,
karşılaştırma bölmesi → Task 2, arayüz → Task 3, ölçüm → Task 4 + 6 + 7,
Anthropic işaretleme → Task 5, doğrulama → Task 2 (otomatik) + Task 9 (elle).
Spec'te olup planda ayrı görev olmayan tek şey münazaranın kapsam dışılığı; bu
Task 3'te `volatileOnly` ile ve Task 8'de dokümantasyonla ele alınıyor.

**Bilinçli belirsizlik:** Üç SDK'nın cache usage üye adları tahmin edilmedi;
Task 5 Step 1 ve Task 6 Step 1 bunları `javap` ile doğrulatan somut adımlar.
Bu bir placeholder değil — komutlar ve beklenen şekil verili.

**İmza tutarlılığı:** `PromptParts(cacheablePrefix, volatileSuffix)`,
`volatileOnly(String)`, `hasCacheablePrefix()`, `joined()`,
`TokenUsage(input, output, cacheRead, cacheWrite)` ve iki argümanlı overload —
plan boyunca aynı.

**Doğrulanan varsayımlar:** `TokenUsage` bir record ve iki argümanlı bir
constructor eklemek ~20 çağrı noktasını korur; `Message` ve `DebateMessage`
token kolonlarını aynı kalıpta tutuyor (`@Column(name = "input_tokens") private Long`);
`ResponseIntensity.applyTo` şu an prompt'un başına ekliyor
(`ResponseIntensity.java:39`); `DebatePromptBuilder.java:29` yalnızca son turu
okuyor.
