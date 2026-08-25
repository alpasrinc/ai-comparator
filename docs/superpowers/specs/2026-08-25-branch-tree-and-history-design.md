# Dallanma Haritası ve Tam Geçmiş — Tasarım

**Tarih:** 2026-08-25
**Durum:** Onaylandı

## Problem

Kullanıcı şu an bir konuşmada yalnızca **son turu** görebiliyor (son kullanıcı mesajı + o mesaja gelen 3 AI cevabı). Konuşma birden fazla tur ilerledikçe:

- Önceki turlarda ne konuşulduğu, hangi AI cevabının seçildiği görünmüyor.
- Konuşmanın dallanma yapısı (README'deki ağaç modeli) kullanıcıya hiç gösterilmiyor; yalnızca backend'de mevcut.

## Kapsam

- Aktif konuşma için katlanır bir "Dallanma haritası" şeridi eklenir (AI panellerinin üstünde).
- Şerit içinde iki alt bölüm: **ağaç görünümü** (tüm dallanma yapısı) ve **tam geçmiş** (aktif yoldaki tüm turların metni).
- Backend değişikliği gerekmiyor — `GET /api/conversations/{id}` zaten tüm mesajları (`id`, `parentMessageId`, `role`, `provider`, `content`, `createdAt`) düz liste olarak dönüyor.

Kapsam dışı: mesaj arama, konuşma yeniden adlandırma/silme, ağaç düğümlerinin sürükle-bırak ile yeniden düzenlenmesi.

## Veri akışı

1. `App.jsx` her turdan sonra (mesaj gönderme tamamlandığında, cevap seçildiğinde, geçmiş bir konuşma açıldığında) `getConversation(conversationId)` çağırarak güncel **tam mesaj listesini** (`conversationDetail.messages`) state'te tutar (`conversationMessages` state'i, yeni).
2. Saf bir yardımcı fonksiyon `buildConversationTree(messages, activeMessageId)` (yeni dosya: `frontend/src/utils/conversationTree.js`) bu düz listeyi ağaca çevirir:
   - Girdi: `[{id, parentMessageId, role, provider, content, createdAt}, ...]`
   - Çıktı: `{ root: TreeNode | null, activePath: Set<number> }`
   - `TreeNode`: `{ message, children: TreeNode[] }`
   - Kök: `parentMessageId === null` olan USER mesajları (normalde tek tane olur, ama fonksiyon birden fazlasını da tolere eder — ilkini kök alır).
   - Her düğümün çocukları: `parentMessageId === node.message.id` olan mesajlar.
   - **Retry dedup kuralı**: Aynı `parentMessageId` altında aynı `provider`'dan birden fazla ASSISTANT mesajı varsa (retry sonucu), yalnızca `createdAt` en yeni olanı ağaçta gösterilir (diğerleri tree'den elenir, ama tam geçmişte zaten sadece aktif yol gösterildiği için bu senaryo orada hiç görünmez).
   - `activePath`: `activeMessageId`'den `parentMessageId` zinciriyle köke kadar yürünerek toplanan mesaj id'leri kümesi (`ConversationService.buildActiveContextPrompt` ile aynı mantık, frontend'e taşınmış hali).
3. Bu fonksiyon saf ve DOM'dan bağımsız olduğu için birim testi yazılabilir (bkz. Test bölümü).

## Bileşenler

### `BranchTreePanel.jsx` (yeni)

Props: `messages` (tam liste), `activeMessageId`, `turnCount` (kapalıyken başlıkta gösterilecek tur sayısı).

- Kapalı durumda: `AI COMPARATOR` başlığının altında, AI panellerinin üstünde ince bir şerit: `▾ Dallanma haritası (N tur)`.
- Tıklanınca aşağı doğru genişler, `▴` ikonuna döner.
- Açıkken iki alt bölüm üst-alt sıralanır (ağaç üstte, tam geçmiş altta — tüm ekran genişliklerinde aynı, ekstra responsive dallanma mantığı gerekmez):
  - **Ağaç görünümü** (`BranchTree.jsx`, yeni alt bileşen): `buildConversationTree` çıktısını recursive render eder. Her USER düğümü bir satır, altında (girintili) o mesaja gelen ASSISTANT dalları küçük renkli rozetler (OpenAI/Claude/Gemini renkleri, mevcut `ai-panel--{provider}` renk paletiyle tutarlı). Aktif yoldaki düğümler dolu/vurgulu, diğerleri soluk (`opacity: 0.5` benzeri).
  - Soluk (aktif olmayan) bir ASSISTANT rozetine tıklanınca, o mesajın içeriği küçük bir açılır kutuda (tooltip/popover) gösterilir. **Aktif dalı değiştirmez** — sadece görüntüler. Dal değiştirmek isteyen kullanıcı mevcut akışı (AI panelindeki "Bu cevapla devam et" butonu) kullanmaya devam eder.
  - **Tam geçmiş** (`ConversationHistory.jsx`, yeni alt bileşen): `activePath` kümesindeki mesajları `createdAt` sırasına göre üstten alta render eder — USER mesajı düz metin, seçilen ASSISTANT cevabı `ReactMarkdown` ile (mevcut `AiPanel` içindeki markdown render mantığıyla aynı stil).

### `App.jsx` değişiklikleri

- Yeni state: `conversationMessages` (tam liste, `BranchTreePanel`'e prop olarak geçilir).
- Yeni fonksiyon: `refreshConversationDetail(id)` — `getConversation(id)` çağırıp `conversationMessages`'ı günceller. Şu noktalarda çağrılır: `handleSend` başarılı olduktan sonra, `handleSelect` (aktif mesaj seçimi) başarılı olduktan sonra, `handleOpenConversation` içinde (zaten `getConversation` çağrılıyor, sonucu `conversationMessages`'a da yazacak).
- `handleNewConversation`'da `conversationMessages` sıfırlanır (`[]`).

## Hata / kenar durumları

- Henüz hiç mesaj yoksa (`conversationId === null`) şerit hiç gösterilmez.
- `conversationMessages` boşsa veya `buildConversationTree` `root: null` dönerse şerit "Dallanma haritası" başlığını göstermez (ya da devre dışı görünür).
- Aynı kullanıcı mesajının birden fazla ASSISTANT çocuğu farklı provider'lardan gelebilir (normal, 3 sağlayıcı) — ağaç bunu olduğu gibi gösterir, sabit "3 çocuk" varsayımı yapılmaz (bir sağlayıcı hata verip hiç mesaj kaydedilmemiş olabilir, o zaman 2 dal görünür).
- Bir USER mesajının birden fazla ASSISTANT'tan farklı zamanlarda gelen (kullanıcı önce bir dalı seçip mesaj gönderip, sonra geri dönüp aynı noktadan başka bir dal seçip başka mesaj gönderirse oluşan) birden fazla USER çocuğu olabilir — ağaç bunu gerçek çoklu dallanma olarak gösterir (tek-çocuk varsayımı yok).
- Çok uzun konuşmalarda hem ağaç hem geçmiş bölümü kendi içinde `overflow-y: auto` ile scroll olur, şerit sayfayı sınırsız büyütmez (`max-height` + scroll).

## Test

- Frontend'de şu ana kadar test altyapısı yok. Bu özellik kapsamında **Vitest** eklenir (React ekosisteminde Vite ile doğal entegrasyon, en düşük sürtünmeli seçenek).
- `conversationTree.test.js`: `buildConversationTree` için birim testleri —
  - Basit tek-tur ağaç (1 USER + 3 ASSISTANT çocuk) doğru kuruluyor mu.
  - Çok turlu, tek yol (dallanmasız) senaryoda `activePath` doğru hesaplanıyor mu.
  - Gerçek dallanma (bir USER mesajının 2 farklı ASSISTANT çocuğundan devam edilmesi) doğru ağaç kuruyor mu.
  - Retry dedup: aynı provider'dan 2 mesaj varsa yalnızca en yenisi ağaçta mı.
  - Boş liste / `activeMessageId: null` durumunda çökmeden `root: null` dönüyor mu.
- UI bileşenleri için (zaman kısıtı gözetilerek) otomatik test eklenmeyecek; manuel tarayıcı testiyle doğrulanacak.
