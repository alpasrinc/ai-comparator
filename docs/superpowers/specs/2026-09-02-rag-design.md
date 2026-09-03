# RAG (Retrieval-Augmented Generation) — Tasarım

- **Tarih:** 2026-09-02
- **Durum:** Onaylandı, uygulanmayı bekliyor
- **Başarı ölçütü:** Kullanıcının yüklediği bir belge hakkında sorulan soruya üç
  sağlayıcının da **aynı kaynak parçalarına dayanarak** cevap vermesi, ve
  cevabın hangi parçalara dayandığının arayüzde görülebilmesi

## Neden

Model, eğitim verisinde olmayan bir şeyi bilemez; bilmediğini sorduğunda
uydurabilir. RAG bunu modeli yeniden eğiterek değil, cevabın dayanağını
**çalışma anında prompt'a koyarak** çözer: soru sorulduğunda ilgili kaynak
parçaları bulunur ve prompt'a iliştirilir.

Bu projede ayrıca doğal bir vitrin var: aynı belge parçalarını üç sağlayıcıya
birden verip cevaplarını yan yana koymak, "aynı kaynağı okuyan üç model ne
kadar farklı yorumluyor" sorusunu görünür kılıyor — projenin var oluş sebebiyle
birebir örtüşüyor.

## Kararlar

| Karar | Seçim | Gerekçe |
|---|---|---|
| Bilgi kaynağı | **Kullanıcının yüklediği belgeler** | En tanınan RAG senaryosu; anlatımı ve gösterimi net |
| Belge kapsamı | **Konuşmaya bağlı** | Şema en sade hali; aday vektör kümesi tanım gereği küçük kalıyor, bu da vektör altyapısı kararını belirliyor |
| Dosya türleri | **PDF + .txt + .md** | PDF için Apache PDFBox; düz metin çıkarma adımı atlandığı için bedava geliyor |
| Vektör saklama | **MySQL `LONGBLOB` + Java'da kosinüs** | MySQL 8.4'te native `VECTOR` yok. Konuşma başına birkaç yüz vektör için ayrı vektör veritabanı, tek dosyayı aramak üzere arama motoru kurmak olurdu |
| Embedding sağlayıcısı | **OpenAI `text-embedding-3-small`** (1536 boyut) | Anthropic'in embedding modeli yok; OpenAI ve Gemini üretebiliyor. Tek uygulama, ince bir arayüzün arkasında |
| Kaynak gösterimi | **Getirilen parçalar arayüzde görünür** | RAG'in doğru parçayı getirdiğini doğrulamanın tek yolu. Modelden atıf istemek sağlayıcıya göre değişken ve güvenilmez |
| Spring AI | **Kapsam dışı** | Projede zaten elle yazılmış `AiProvider` seam'i var; Spring AI kendi `ChatClient` soyutlamasını getirip aynı işi yapan ikinci bir katman oluşturur. Ayrıca Spring Boot 4 uyumluluğu doğrulanmadı |
| Yükleme | **Senkron, tek transaction** | 50 sayfalık PDF tek batch embedding çağrısıyla 1-2 sn. Asenkron yapmak polling, durum makinesi ve yarış koşulları getirirdi |

## Mimari

### Bileşenler

Mevcut `AiProvider` seam'ine dokunulmuyor; yanına **ayrı** bir seam ekleniyor:

```
ai/EmbeddingProvider.java            arayüz: embed, embedBatch, modelName
ai/OpenAiEmbeddingProvider.java      tek uygulama

service/DocumentTextExtractor.java   PDF (PDFBox) | metin -> düz metin
service/TextChunker.java             metin -> örtüşmeli parçalar   (saf fonksiyon)
service/VectorMath.java              kosinüs, float[] <-> byte[]   (saf fonksiyon)
service/DocumentIngestionService.java  çıkar -> parçala -> göm -> kaydet
service/DocumentRetrievalService.java  soru -> göm -> kosinüs -> ilk K

controller/DocumentController.java   yükle / listele / sil
```

`TextChunker` ve `VectorMath` bilinçli olarak saf: veritabanı, ağ, Spring yok.
RAG'in doğru çalışıp çalışmadığını belirleyen mantığı taşıyorlar ve tek
başlarına, milisaniyeler içinde test edilebilir olmaları gerekiyor.

### Veri modeli (`V6__add_rag_documents.sql`)

```sql
CREATE TABLE documents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    chunk_count     INT NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_documents_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE
);
CREATE INDEX idx_documents_conversation ON documents (conversation_id);

CREATE TABLE document_chunks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id     BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding       LONGBLOB NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_chunks_document
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);

CREATE TABLE message_sources (
    message_id  BIGINT NOT NULL,
    chunk_id    BIGINT NOT NULL,
    similarity  DOUBLE NOT NULL,
    rank_order  INT NOT NULL,
    PRIMARY KEY (message_id, chunk_id),
    CONSTRAINT fk_sources_message
        FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_sources_chunk
        FOREIGN KEY (chunk_id) REFERENCES document_chunks (id) ON DELETE CASCADE
);
```

Üç karar burada gizli:

**`embedding_model` her satırda saklanıyor.** Model değişirse eski vektörler
yeni sorgu vektörüyle kıyaslanamaz ve arama sessizce saçmalamaya başlar. Sorgu
anında uyuşmazlık varsa gürültülü hata veriliyor — sessizce yanlış sonuç değil.

**Kaynaklar KULLANICI mesajına bağlanıyor**, asistan mesajına değil. Aynı
parçalar üç sağlayıcıya da gidiyor; bilgi tura ait, cevaba değil. Üç kopya
kayıt da önlenmiş oluyor.

**`status` kolonu yok.** Yükleme tek transaction: ya belge + tüm parçalar
birlikte kaydedilir ya hiçbiri. Yarım kalmış belge diye bir durum olmadığı için
durum makinesi de gerekmiyor.

## Veri akışı

### DTO'lar ve kaynak bloğunun biçimi

```java
// Retrieval sonucu; prompt kurucusuna ve frontend'e aynı tip gider.
public record RetrievedChunk(
        Long chunkId,
        Long documentId,
        String filename,
        int chunkIndex,
        String content,
        double similarity
) {}
```

`CompareResponse` ve `AiResponse`'a iki alan ekleniyor:
`List<RetrievedChunk> sources` ve `boolean sourcesUnavailable`. Kaynaklar tura
ait olduğu için `CompareResponse` seviyesinde duruyor, sağlayıcı başına
tekrarlanmıyor.

Akış (SSE) tarafında `start` olayının gövdesi bu iki alanla genişliyor; yeni
bir olay türü eklenmiyor.

Prompt'a giren kaynak bloğunun biçimi **sabit** olmalı — prompt'un byte
düzeyinde öngörülebilir kalması buna bağlı:

```
Aşağıdaki kaynaklara dayanarak cevap ver. Kaynaklarda olmayan bir şeyi
uydurma; bilgi kaynaklarda yoksa bunu söyle.

[1] rapor.pdf (parça 12)
<parça metni>

[2] notlar.md (parça 3)
<parça metni>

```

Blok yalnızca eşiği geçen parça varsa ekleniyor; yoksa `volatileSuffix` bugünkü
hâliyle kalıyor.

### Yükleme (senkron)

```
POST /api/conversations/{id}/documents   (multipart)
  -> boyut ve tür kontrolü
  -> DocumentTextExtractor: PDF/metin -> düz metin
  -> TextChunker: ~1000 karakterlik, ~150 karakter örtüşmeli parçalar
     (önce "\n\n", sonra cümle sonu sınırlarında bölmeye çalışır)
  -> EmbeddingProvider.embedBatch(parçalar)      <- tek API çağrısı
  -> vektörler normalize edilip byte[]'e yazılır
  -> documents + document_chunks tek transaction'da kaydedilir
```

Vektörler **yazılırken normalize ediliyor**; böylece sorgu anında kosinüs
benzerliği nokta çarpımına iniyor — hem daha hızlı hem daha az hata yüzeyi.

### Sorgu

```
kullanıcı mesajı gönderir
  -> konuşmanın belgesi var mı?
     evet -> DocumentRetrievalService.retrieve(conversationId, soru)
             soruyu göm -> o konuşmanın parçalarıyla nokta çarpımı
             -> skoru eşiğin üstünde olan ilk K parça
  -> AYNI parçalar üç sağlayıcıya da gider     <- karşılaştırmanın adil kalması için
  -> her sağlayıcı için prompt:
       cacheablePrefix : kimlik preamble + dal transcript'i        (DEĞİŞMEDİ)
       volatileSuffix  : KAYNAKLAR + kullanıcı turu
  -> kaynaklar message_sources'a kullanıcı mesajıyla kaydedilir
  -> cevapla birlikte frontend'e gönderilir
```

Yoğunluk yönergesinin yeri değişmiyor: `ConversationService` yalnızca
`KAYNAKLAR + kullanıcı turu`nu `volatileSuffix` olarak üretiyor, yönergeyi
sağlayıcı `intensity.applyTo(volatileSuffix)` ile en başa ekliyor. Modele giden
sıra dolayısıyla: prefix → yönerge → kaynaklar → soru.

Retrieval **bir kez** çalışıyor, sağlayıcı başına değil. Bu yüzden imza
değişiyor:

```java
public PromptParts buildActiveContextPrompt(
        Long conversationId,
        String newUserContent,
        AiProviderType targetProvider,
        List<RetrievedChunk> sources   // yeni
)
```

**Kaynaklar `volatileSuffix`'e giriyor, `cacheablePrefix`'e değil.** Getirilen
parçalar soruya göre değişiyor, yani stabil değiller; prefix'e koymak her soruda
tüm cache'i geçersiz kılardı.

**Benzerlik eşiği** küçük ama önemli: soru belgeyle alakasızsa en yakın K
parçayı yine de prompt'a tıkmak modele gürültü enjekte etmek olur. Eşiğin
üstünde hiçbir şey yoksa kaynak bloğu hiç eklenmiyor.

### "Tekrar dene" yolu

Retry sırasında retrieval **yeniden çalıştırılmıyor**; kaynaklar
`message_sources`'tan okunuyor. Bir embedding çağrısı tasarruf ediliyor, ve
daha önemlisi prompt ilk denemeyle byte-byte aynı kalıyor — yani prompt cache
okuması retry'da çalışmaya devam ediyor.

## Hata yönetimi

Yükleme hataları **sert**: hiçbir şey kaydedilmez.

| Durum | Yanıt |
|---|---|
| Desteklenmeyen tür | 400 — "Yalnızca PDF, .txt ve .md desteklenir." |
| Boyut aşımı | 413 — "Dosya çok büyük (en fazla 5 MB)." |
| PDF'ten metin çıkmadı | 400 — "Bu PDF'ten metin çıkarılamadı; taranmış bir belge olabilir." |
| Boş / yalnızca boşluk | 400 |
| Embedding API hatası | 502, transaction geri alınır |

Taranmış PDF'e ayrı mesaj ayrıldı: dosya sorunsuz yüklenir, 0 parça çıkar, arama
hep boş döner ve kullanıcı sebebini anlamaz.

Sorgu anında felsefe farklı, ve fark bilinçli:

- **Geçici hata → düşerek devam et.** Embedding servisi cevap vermezse sohbet
  öldürülmüyor; loglanıp kaynaksız devam ediliyor (bir sağlayıcı patlayınca
  diğerlerinin devam etmesiyle aynı mantık). **Ama sessizce değil:** yanıtta
  `sourcesUnavailable` bayrağı dönüyor ve arayüz bunu söylüyor.
- **Doğruluğu bozan hata → gürültülü patla.** Bir parçanın `embedding_model`
  değeri mevcut modelden farklıysa o vektörü kıyaslamak anlamsız sayılar üretir
  — çalışır görünen ama yanlış bir sistem. Burada düşerek devam etmek yanlış.

**Kapatılması gereken açık:** `AiRateLimitFilter` yalnızca `/api/chat/**`'i
koruyor. Yükleme endpoint'i embedding API'sini çağırıyor, yani paralı, ama
`/api/conversations/**` altında olduğu için filtrenin göremediği yerde.
Filtrenin koruduğu yol listesi yapılandırılabilir yapılıp yükleme de kapsama
alınmalı.

## Test stratejisi

**Saf birim testler** (DB yok, ağ yok):

- `TextChunkerTests` — örtüşme, paragraf sınırında bölme, çok kısa metin, boş
  metin, parça boyutundan uzun tek kelime, Türkçe karakterlerde karakter sayımı
- `VectorMathTests` — kosinüs bilinen vektörlerde (aynı = 1, dik = 0, zıt = −1),
  `float[] -> byte[] -> float[]` gidiş-dönüşünün kayıpsızlığı, normalize
  vektörde nokta çarpımının kosinüse eşitliği
- `DocumentTextExtractorTests` — fixture PDF ve .txt; metin çıkmayan PDF'te
  doğru istisna

**Mock'lu servis testleri:**

- `DocumentIngestionServiceTests` — parça sayısı, model adının satıra yazılması,
  embedding hatasında hiçbir şeyin kalıcı olmaması
- `DocumentRetrievalServiceTests` — sıralama, eşik altının elenmesi, sonuç
  yoksa boş liste, model uyuşmazlığında hata

**Entegrasyon:** gerçek multipart yükleme, listeleme, silme, ve konuşma
silinince belgelerin cascade ile gitmesi. CI'da placeholder anahtarlar olduğu
için `EmbeddingProvider` bir `@TestConfiguration` stub'ıyla değiştiriliyor —
hiçbir test gerçek API çağırmıyor.

**Asıl bekçi test** — caching'deki `cacheablePrefix` testinin RAG karşılığı:

```java
@Test
void retrievedSourcesNeverEnterTheCacheablePrefix() {
    PromptParts withSources = conversationService.buildActiveContextPrompt(
            convId, "soru", ANTHROPIC, List.of(chunk("BELGE PARCASI")));
    PromptParts withoutSources = conversationService.buildActiveContextPrompt(
            convId, "soru", ANTHROPIC, List.of());

    assertThat(withSources.cacheablePrefix())
            .isEqualTo(withoutSources.cacheablePrefix())
            .doesNotContain("BELGE PARCASI");
    assertThat(withSources.volatileSuffix()).contains("BELGE PARCASI");
}
```

Kaynaklar prefix'e sızarsa cache her soruda ölür ve kimse fark etmez. İki sistem
de sessizce bozuluyor; kesişimleri iki kat sessiz.

## Arayüz

- Karşılaştırma ekranına **"Dosya ekle"** düğmesi; yüklenen belgeler çip olarak
  (ad, parça sayısı, silme).
- Yükleme sırasında spinner, hata durumunda net mesaj.
- **Kaynaklar üç panelin üstünde tek blokta**, panel başına değil — üç
  sağlayıcıya aynı parçalar gidiyor. Her satırda `belge.pdf · parça 12 · %78`
  ve metnin ilk ~200 karakteri.
- Akışta (SSE) kaynaklar `start` olayıyla gönderiliyor; retrieval streaming
  başlamadan bittiği için token'lardan önce görünüyorlar.
- Geçmişten açılan konuşmalarda kaynaklar `message_sources`'tan geri yükleniyor.

Yeni dosyalar: `components/DocumentUploader.jsx`, `components/SourceList.jsx`;
`services/api.js`'e yükleme/listeleme/silme.

## Yapılandırma

```properties
rag.enabled=${RAG_ENABLED:true}
rag.max-file-size-bytes=${RAG_MAX_FILE_SIZE_BYTES:5242880}
rag.chunk-size-chars=${RAG_CHUNK_SIZE_CHARS:1000}
rag.chunk-overlap-chars=${RAG_CHUNK_OVERLAP_CHARS:150}
rag.top-k=${RAG_TOP_K:4}
rag.min-similarity=${RAG_MIN_SIMILARITY:0.30}
openai.embedding-model=${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
spring.servlet.multipart.max-file-size=6MB
spring.servlet.multipart.max-request-size=6MB
```

Spring'in multipart limiti (6 MB) bilinçli olarak kendi limitimizin (5 MB)
üstünde: kendi kontrolümüz devreye girip anlaşılır bir mesaj döndürebilsin,
Spring'in ham hatası son çare olarak kalsın.

`rag.enabled=false` retrieval'ı devre dışı bırakır ve yükleme endpoint'ini
kapatır — embedding anahtarı olmayan ortamlarda projenin çalışmaya devam etmesi
için.

## Caching spec'inde bir düzeltme

`2026-09-02-prompt-caching-design.md` şunu iddia ediyor:

> "model yükseltilirse veya prompt'ları büyüten bir özellik (ör. RAG) eklenirse
> kazanç kendiliğinden büyür"

Bu yanlış. RAG prompt'u büyütüyor ama büyüttüğü kısım `volatileSuffix` —
cache'lenmeyen yarı. Cache kazancı transcript'in büyümesinden geliyor, kaynak
bloğundan değil. İkisi birbirini beslemiyor, dik kesişiyor. O cümle bu iş
kapsamında düzeltilecek.

## Kapsam dışı

- **Münazara modunda RAG** — münazara akışı zaten en karmaşık parça
- **Global belge kütüphanesi** — konuşmaya bağlı kapsam seçildi; kütüphaneye
  geçilirse vektör saklama kararı da yeniden açılır
- **DOCX / HTML / OCR** — her format kendi kenar durumlarını getirir, RAG'in
  kendisine kattığı bir şey yok
- **Yeniden sıralama (reranking) ve hibrit arama (BM25 + vektör)** — bu korpus
  boyutunda karşılığı yok
- **Ayrı vektör veritabanı** — konuşma başına birkaç yüz vektör için gereksiz
