# Akış İptali ve Münazara Rate Limit — Tasarım

- **Tarih:** 2026-09-02
- **Durum:** Onaylandı, uygulanmayı bekliyor
- **Kapsam:** Sağlamlaştırma yol haritasının 1. paketi (bkz. "Kapsam dışı")

## Problem

Kod incelemesinde iki somut defect doğrulandı.

### 1. İptal edilen akışlar backend'de durmuyor

`SseSupport.java:28` istemciye gönderim başarısız olduğunda hatayı yutuyor
(`log.debug` ile geçiyor). `DebateOrchestrator.java:80` ve
`AiComparisonService.java:211`'de `emitter.onError(throwable -> { })` **boş**,
`onCompletion` ise hiç kayıtlı değil.

Sonuç: istemci bağlantıyı kestiğinde (Durdur, sekme kapatma, sayfa yenileme, ağ
kopması) orkestratörün haberi olmuyor ve kalan tüm sağlayıcı çağrılarını sonuna
kadar yapıyor.

3 katılımcılı × 5 turluk bir münazara 1. turda terk edilirse, kimse dinlemiyor
olmasına rağmen **12 sağlayıcı çağrısı + sentez** çalışmaya devam ediyor.

Karşılaştırma modunda patlama yarıçapı küçük (sağlayıcı başına 1 çağrı) ama ek
bir yan etkisi var: cevap tamamlanıp `saveRetriedResponse` ile DB'ye yazılıyor,
yani kullanıcının hiç görmediği bir cevap geçmişte "seçilebilir" olarak
görünüyor.

### 2. Rate limit en pahalı ucu korumuyor

`AiRateLimitFilter.java:42` yalnızca `/api/chat/` ile başlayan yolları
filtreliyor. `/api/debates/stream` tamamen korumasız — üstelik tek isteği
*katılımcı × tur + 1* kadar AI çağrısı üretiyor, yani korumalı uçtan bir
büyüklük mertebesi daha pahalı.

## Kararlar

| Karar | Seçim | Gerekçe |
|---|---|---|
| İptal edilen işin yarım sonucu | **Saklanır** | Token'lar zaten ödendi; çıktıyı atmak ikinci bir kayıp olur. Münazara `CANCELLED` durumuyla geçmişte görünür |
| Münazara rate limit modeli | **Ayrı ve daha sıkı kova** | Maliyet ağırlıklı kova daha adil olurdu ama filtrenin JSON gövdeyi okumasını gerektirir (`ContentCachingRequestWrapper`); riski kapatmak için gereğinden karmaşık |
| Durdur kontrolü | **Mevcut gönder butonu durum değiştirir** | `ChatInput`'ta `isLoading` iken buton zaten devre dışı bir ölü piksel; ayrı buton input alanında iki kontrol yan yana bırakırdı |

## Tasarım

### 1. `StreamSession` — iptal sinyali

Bugün her akışta üç şey elden dolaştırılıyor: `emitter`, `lock`, `SseSupport`.
Bunlar tek bir nesnede toplanıyor ve iptal bayrağı da içine giriyor.
`SseSupport` bu sınıfla değiştirilir.

```java
// service/StreamSession.java
public final class StreamSession {
    private final SseEmitter emitter;
    private final Object lock = new Object();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean isCancelled();
    public void cancel();
    public boolean send(String eventName, Object data); // emit patlarsa cancel() + false
}
```

Emitter bağlantıları (bugün boş olan yerler):

- `emitter.onError(t -> session.cancel())`
- `emitter.onCompletion(session::cancel)` — şu an hiç kayıtlı değil
- `emitter.onTimeout(...)` — önce `cancel()`, sonra `complete()`

Kontrol noktaları:

| Yer | Davranış |
|---|---|
| `DebateOrchestrator.execute`, her tur başında | Kalan turlar başlamaz; münazara `CANCELLED` işaretlenir |
| `runRound`, katılımcı dağıtmadan önce | Henüz başlamamış çağrılar yapılmaz |
| Token callback'i (debate + compare) | `StreamCancelledException` fırlatılır → SDK akışı ortasından kesilir. Akan bir çağrıyı durdurmanın tek yolu budur |
| `runSynthesis` öncesi | Sentez (en pahalı tek çağrı) hiç yapılmaz |

Kısmi sonuç: iptal anında `accumulated` boş değilse normal kaydetme yoluna
girer. `DebateStatus` VARCHAR olarak saklandığı için `CANCELLED` değerini
enum'a eklemek yeterlidir — **migration gerekmez**.

### 2. Rate limit

`AiRateLimitFilter` iki kurallı hale gelir; tek kova haritası yerine iki ayrı
kova haritası tutar.

| Kural | Kapasite | Dolum | Ayar anahtarı |
|---|---|---|---|
| `POST /api/chat/**` | 20 | 3 sn/jeton | `ai.rate-limit.capacity` (mevcut) |
| `POST /api/debates/**` | 5 | 30 sn/jeton | `ai.rate-limit.debate.capacity`, `ai.rate-limit.debate.refill-interval-seconds` (yeni) |

Yalnızca `POST` sınırlanır:

```java
boolean isPaidCall = "POST".equals(request.getMethod())
        && (uri.startsWith("/api/chat/") || uri.startsWith("/api/debates/"));
```

Gerekçe: `/api/debates/**` altında `GET /api/debates` (geçmiş listesi),
`GET /api/debates/{id}` (münazara açma) ve `DELETE` de var. Yalnızca yola
bakılırsa geçmişten 6 münazara açan kullanıcı 429 alır — üstelik o istekler tek
bir AI çağrısı bile yapmaz.

Karşılaştırma tarafı zaten tümüyle POST olduğu için `/api/chat/**` davranışı
değişmez; mevcut testler aynen geçmelidir.

### 3. Durdur butonu (frontend)

**`api.js`** — `AbortController` bugün fonksiyon içinde kapalı
(`api.js:100`, `api.js:231`). İki akış fonksiyonu opsiyonel bir `signal` alır;
içerideki idle-timeout controller'ı kalır, dış sinyal ona bağlanır:

```js
signal?.addEventListener('abort', () => {
  cancelledByUser = true
  controller.abort()
})
```

Kritik ayrım: idle timeout ve kullanıcı iptali aynı `AbortError`'ı üretir.
Bugünkü `catch` ikisini de "İstek zaman aşımına uğradı. Tekrar deneyin."
mesajına çeviriyor (`api.js:159`). `cancelledByUser` bayrağıyla ayrılır —
**kullanıcı iptali sessizce biter**, timeout eskisi gibi hata fırlatır.

Kontrollerin yeri:

| Mod | Yer | Davranış |
|---|---|---|
| Karşılaştırma | `ChatInput`'taki mevcut gönder butonu | `isLoading` iken "⏹ Durdur"a dönüşür ve tıklanabilir olur |
| Münazara | `DebateView`, transcript başlığının yanı | `isRunning` iken "⏹ Durdur" (launcher gizli olduğu için input'ta yer yok) |

Durdurma sonrası: `AiPanel`'e `Durduruldu` statüsü eklenir
(`statusState: 'stopped'`, nötr gri). Akan metin **silinmez** — kısmi sonuç
saklama kararıyla tutarlı olarak ekranda ve DB'de kalır. Münazarada durdurulan
tur girdileri yerinde kalır, transcript "durduruldu" rozetiyle biter.

## Doğrulama

### Otomatik (backend)

| Test sınıfı | Eklenen vaka |
|---|---|
| `DebateOrchestratorTests` | İptal sonrası sonraki tur hiç çağrılmaz (sahte sağlayıcıda çağrı sayacı); sentez atlanır; münazara `CANCELLED` işaretlenir; tamamlanmış turlar DB'de kalır |
| `AiComparisonServiceTests` | İptal anında biriken kısmi metin kaydedilir; boşsa kaydedilmez |
| `AiRateLimitFilterTests` | Münazara POST'u kendi kovasını tüketir; `GET /api/debates` sınırlanmaz; iki kova birbirini etkilemez |

Frontend testi bu pakette **yazılmaz**: `vite.config.js` `environment: 'node'`
kullanıyor ve `api.js` `window.setTimeout` çağırıyor. jsdom kurulumu Paket 2'nin
işi.

### Elle

Asıl kanıt buradadır; otomatik test "para yanmıyor"u ispatlamaz.

1. 3 katılımcı × 3 tur münazara başlat, 1. tur akarken **Durdur** → backend
   logunda 2. tura ait sağlayıcı çağrısı görünmemeli
2. Aynısını **sekmeyi kapatarak** tekrarla → aynı sonuç (asıl senaryo budur;
   Durdur butonu yalnızca görünen yüzü)
3. Geçmişte `CANCELLED` kayıt görünür, açılınca 1. tur cevapları durur
4. Arka arkaya 6 münazara → 6.'sında 429
5. Geçmişten 10 münazara aç → 429 **yok**
6. Compare'de Durdur → kısmi cevap ekranda kalır, hata mesajı çıkmaz

1. ve 2. madde iki loglama noktasından izlenir: `DebateOrchestrator` tur
başlangıcı ve sağlayıcı çağrısı.

## Kapsam dışı

Bu spec yalnızca 1. paketi kapsar. Sonraki paketler ayrı spec'ler olarak
yazılacaktır.

- **Paket 2 — Test & yapı:** `@testing-library/react` + jsdom kurulumu,
  `CompareView`/`DebateView` davranış testleri, 20 `useState`'in
  `useReducer`/custom hook'a taşınması, error boundary
- **Paket 3 — Deneyim:** cevabı kopyala, yanıt süresi rozeti, Markdown export,
  geçmişte arama, tema anahtarı

Sıra 1 → 2 → 3'tür: Paket 2'nin testleri Paket 1'in getirdiği iptal davranışını
da kapsamalı; testler refactor'dan önce yazılmalı (500 satırlık `CompareView`'de
güvenlik ağı onlardır); Paket 3'ün UI eklemeleri refactor'dan sonra gelmeli ki
aynı iş iki kez yapılmasın.

Üçüncü sekme fikri (Arena / kör oylama + skor panosu) bu yol haritasından
sonraya bırakıldı.
