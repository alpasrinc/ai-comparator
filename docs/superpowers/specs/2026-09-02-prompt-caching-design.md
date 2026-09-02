# Prompt Caching — Tasarım

- **Tarih:** 2026-09-02
- **Durum:** Onaylandı, uygulanmayı bekliyor
- **Başarı ölçütü:** Kazancın mutlak büyüklüğü değil, cache'in **çalıştığının kanıtlanabilir** olması

## Neden

Prompt caching tek bir değişmeze dayanır:

> Cache bir **prefix eşleşmesidir.** Prefix'in herhangi bir yerindeki tek bir byte değişikliği, o noktadan sonrasının tamamını geçersiz kılar.

Bu yüzden iş, "işaretleyici koymak" değil, prompt'un **stabil kısmının fiziksel olarak değişken kısımdan önce gelmesini** sağlamaktır. İşaretleyici ikincil.

Modeller bu projede en ucuz katmanda (`claude-haiku-4-5`, `gpt-5.6-luna`,
`gemini-3.5-flash-lite`), dolayısıyla mutlak para kazancı küçüktür. İşin değeri
doğru kurulmuş ve ölçülebilir olmasında: model yükseltilirse veya prompt'ları
büyüten bir özellik (ör. RAG) eklenirse kazanç kendiliğinden büyür.

## Kod incelemesinin bulguları

Başlangıçtaki varsayımların ikisi kodda doğrulanmadı.

### 1. Münazarada büyüyen prefix yok

`DebatePromptBuilder.java:29`:

```java
Map<AiProviderType, String> previousRound = transcript.get(transcript.size() - 1);
```

Eleştiri turu tüm transcript'i değil **yalnızca bir önceki turu** gönderiyor —
kayan pencere, büyüyen prefix değil. Turlar arasında stabil kalan kısım sadece
"kimliğin + konu", ~50 token; her modelin minimum eşiğinin çok altında.

`buildSynthesisPrompt` tam transcript'i gönderir ama münazara başına **bir kez**
çağrılır. Tek çağrının cache'lenmesi hiçbir okuma üretmez.

**Sonuç: münazara modu bu spec'in cache kazancı kapsamı dışındadır.**

### 2. `CLAUDE.md` ile kod çelişiyor (ayrı bir konu)

`CLAUDE.md` şunu iddia ediyor:

> "Round N+1's prompt is built from the full transcript of rounds 1..N" …
> "the in-memory transcript is the debate's memory — the models are stateless,
> the orchestrator carries the history"

Kod bunu yapmıyor: 3. tur, 1. turu hiç görmez. Ya doküman yanlış ya da
orkestratör münazara hafızasını kaybediyor. **Bu spec bunu düzeltmiyor**, ama
kayda geçiriyor: düzeltilirse münazarada da büyüyen bir prefix oluşur ve
caching orada da anlamlı hale gelir.

### 3. Asıl fırsat karşılaştırma modunda

`ConversationService.buildActiveContextPrompt` gerçek bir büyüyen prefix üretir:
kimlik preamble'ı + aktif dalın transcript'i (turlar arası byte-byte aynı) +
yeni kullanıcı mesajı. Ders kitabı örneği.

Ama `ResponseIntensity.applyTo` (`ResponseIntensity.java:39`) yönergeyi
**prompt'un en başına** ekliyor ve üç sağlayıcı da onu böyle çağırıyor. Pozisyon
0'daki bir değişiklik her şeyi geçersiz kıldığı için, intensity değiştiği anda
hiçbir sağlayıcıda cache tutmaz — OpenAI ve Gemini'nin *otomatik* cache'i dahil.

## Kararlar

| Karar | Seçim | Gerekçe |
|---|---|---|
| Anthropic modeli | **Değiştirilmiyor** (`claude-haiku-4-5`) | Haiku 4.5'in minimum cache'lenebilir prefix'i 4096 token — en yüksek kademe. Sonnet 5'e geçmek eşiği 1024'e indirirdi ama girdi fiyatını $1 → $2/MTok'a çıkarırdı; model fiyatı cache'in kazandırdığından fazlasını geri alır |
| Kapsam | **Anthropic açık, OpenAI + Gemini otomatik** | Açık işaretleme gerektiren tek sağlayıcı Anthropic. Diğer ikisinde yapılacak tek iş prefix'i stabil tutmak |
| Gemini `CachedContent` | **Kapsam dışı** | Ayrı yaşam döngüsü (oluştur / TTL yönet / sil). Bu korpus boyutunda taşıyacağı ağırlık yok; flash-lite'ta zaten implicit cache var |
| TTL | **5 dakika** (yapılandırılabilir) | 1 saatlik TTL yazma primini 2×'e çıkarır ve başabaş için 3+ istek gerektirir. Karşılaştırma modunda ardışık turlar genelde 5 dakikadan yakın |
| Yanıt (response) cache'i | **Kapsam dışı** | Aynı prompt için cevabı DB'de tutmak ayrı bir katman; "tekrar dene" diyen kullanıcı farklı cevap bekler |

## Tasarım

### 1. `PromptParts` — stabil/değişken ayrımı

`AiProvider.streamMessage(String userMessage, …)` düz bir string alıyor; "şu
kısım stabil" bilgisini taşıyacak yer yok. Küçük bir değer tipi ekleniyor:

```java
// dto/PromptParts.java
public record PromptParts(String cacheablePrefix, String volatileSuffix) {

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

`AiProvider`'ın üç metodu (`sendMessage`, `streamMessage`,
`streamSynthesisMessage`) `String` yerine `PromptParts` alır.

### 2. Karşılaştırma modunda bölme noktası

`buildActiveContextPrompt` artık `PromptParts` döndürür:

| Parça | İçerik |
|---|---|
| `cacheablePrefix` | kimlik preamble'ı + aktif dalın transcript'i (yeni mesaj hariç) |
| `volatileSuffix` | intensity yönergesi + `USER: <yeni mesaj>\n\nASSISTANT:` |

Intensity'nin yeri ince bir nokta: yönerge "en sona" konamaz, çünkü prompt
`ASSISTANT:` ile biter ve ondan sonraki metin modelin kendi ağzından çıkmış gibi
görünür. Doğru yer prefix'in bittiği, yeni kullanıcı mesajının başladığı
noktadır. Bu yüzden `ResponseIntensity.applyTo` "başa ekle" yerine "değişken
kısmın başına ekle" anlamına gelir ve **sağlayıcılar onu artık kendileri
çağırmaz** — prompt'u kuran taraf çağırır.

Münazara `PromptParts.volatileOnly(...)` kullanır: prompt hijyeninden yararlanır,
cache işaretlemesi almaz.

### 3. Anthropic'te açık işaretleme

Tek breakpoint, prefix'in sonunda — dokümantasyondaki "shared prefix, varying
suffix" kalıbı. Prompt tek bir user mesajının iki content bloğuna bölünür;
`system` alanına taşınmaz, böylece prompt'un anlamı değişmez:

```java
MessageCreateParams.builder()
        .model(model)
        .maxTokens(outputTokenLimit)
        .addUserMessageOfBlockParams(List.of(
                ContentBlockParam.ofText(TextBlockParam.builder()
                        .text(parts.cacheablePrefix())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()),
                ContentBlockParam.ofText(TextBlockParam.builder()
                        .text(parts.volatileSuffix())
                        .build())))
        .build();
```

Prefix boşsa (münazara) tek bloklu, işaretsiz haline düşer.

**Eşik için heuristic gerekmiyor.** Dokümantasyon, eşiğin altındaki bir prefix'in
işaretlenmiş olsa bile sessizce cache'lenmediğini ve `cache_creation_input_tokens`
değerinin 0 döndüğünü söylüyor — yani yazma primi de ödenmez. İşaretleme her
zaman yapılır; eşiğin altında kendiliğinden etkisizdir.

### 4. Ölçüm

`TokenUsage` iki alan kazanır. Mevcut ~20 çağrı noktasının bozulmaması için
iki argümanlı bir constructor korunur:

```java
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
) {
    public TokenUsage(long inputTokens, long outputTokens) {
        this(inputTokens, outputTokens, 0, 0);
    }
    …
}
```

Sağlayıcı başına okuma noktaları:

| Sağlayıcı | Cache okuma | Cache yazma |
|---|---|---|
| Anthropic | `usage().cacheReadInputTokens()` | `usage().cacheCreationInputTokens()` |
| OpenAI | `usage().inputTokensDetails()` altındaki cached alanı | — (otomatik, ayrı yazma kavramı yok) |
| Gemini | `usageMetadata().cachedContentTokenCount()` | — |

SDK'ların tam üye adları uygulama sırasında `javap` ile doğrulanacak; tahmin
yürütülmeyecek.

Kalıcılık: `V5__add_cache_token_usage.sql`, `messages` ve `debate_messages`
tablolarına iki nullable BIGINT kolon. Frontend'de `TokenUsageBadge` cache
okuması sıfırdan büyükse bunu gösterir.

**`input_tokens` artık toplam prompt boyutu değildir.** Toplam =
`input_tokens + cache_read + cache_write`. Rozet ve dokümantasyon bunu yansıtmalı.

### 5. Doğrulama

**Asıl regresyon koruması bir API çağrısı gerektirmiyor.** Caching en sık
sessizce bozulur: kod çalışmaya devam eder, sadece fatura artar. Bunu yakalayan
test, aynı konuşmanın ardışık iki turu için prompt üretip birincinin
`cacheablePrefix`'inin ikincisinin **byte-byte öneki** olduğunu doğrular:

```java
assertThat(next.cacheablePrefix()).startsWith(current.cacheablePrefix());
```

Bu test, intensity'nin öne kaçması, preamble'a tarih/ID sızması, transcript
sırasının değişmesi gibi tüm sessiz geçersiz kılıcıları yakalar.

Ek testler: intensity yönergesinin `volatileSuffix` içinde olduğu ve
`cacheablePrefix`'in intensity'den bağımsız olduğu (LOW ve HIGH için aynı prefix).

**Elle doğrulama** (gerçek kazancın kanıtı): Anthropic seçili tek sağlayıcıyla
uzun bir konuşma yürüt; dal geçmişi 4096 token'ı aştıktan sonraki turda
`cacheReadTokens > 0` görülmeli. Haiku 4.5'in eşiği yüzünden bu tipik olarak
8-10 tur sürer — bu sınır dokümante edilecek, gizlenmeyecek.

## Kapsam dışı

- **Münazara modunda cache kazancı** — büyüyen prefix yok (bkz. Bulgu 1)
- **Münazara hafızası düzeltmesi** — ayrı iş; düzeltilirse caching orada da anlamlı olur (bkz. Bulgu 2)
- **Anthropic modelini değiştirmek** — 4096 token eşiği kabul edildi
- **Gemini `CachedContent`** — ayrı yaşam döngüsü, bu boyutta karşılığı yok
- **Yanıt (response) cache'i** — farklı bir katman, farklı ürün kararı
- **1 saatlik TTL** — yapılandırılabilir bırakılıyor, varsayılan 5 dakika
