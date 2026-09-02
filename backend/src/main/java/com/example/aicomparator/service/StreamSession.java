package com.example.aicomparator.service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tek bir SSE akışının durumu: emitter, gönderim kilidi ve iptal bayrağı.
 *
 * <p>İstemci bağlantıyı kestiğinde ya da zaman aşımına uğradığında (sekme
 * kapatma, Durdur, ağ kopması) iptal bayrağı kalkar — bizim başlattığımız
 * bir tamamlanma bunu iptal saymaz. Orkestratörler bu bayrağı turlar
 * arasında ve token callback'lerinde okuyup kalan sağlayıcı çağrılarını
 * yapmaz — aksi halde kimse dinlemiyorken ücretli çağrılar sürer.
 */
public final class StreamSession {

    private static final Logger log =
            LoggerFactory.getLogger(StreamSession.class);

    private final SseEmitter emitter;
    private final Object lock = new Object();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public StreamSession(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onError(throwable -> cancel());
        emitter.onCompletion(() -> {
            if (!completed.get()) {
                cancel();
            }
        });
        emitter.onTimeout(() -> {
            cancel();
            complete();
        });
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Olayı gönderir. İptal edilmişse hiç göndermez.
     *
     * <p>Gönderim sırasında istemcinin gerçekten gittiğini gösteren bir
     * {@link IOException} ya da emitter'ın zaten tamamlanmış olduğunu
     * gösteren cause'suz bir {@link IllegalStateException} yakalanırsa
     * akışı iptal eder. Bir serileştirme hatasını (cause'lu
     * {@code IllegalStateException}) ya da beklenmeyen herhangi bir
     * hatayı ise sadece loglar ve o tek frame'i düşürür — sunucu
     * kaynaklı bir hata, hâlâ bağlı bir istemciyi iptal etmemeli.
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
            } catch (IOException clientGone) {
                log.debug(
                        "SSE gönderimi başarısız, istemci gitmiş sayılıyor: {}",
                        clientGone.toString()
                );
                cancel();
                return false;
            } catch (IllegalStateException illegalState) {
                if (illegalState.getCause() != null) {
                    // Serileştirme hatası: sunucu tarafı sorunu, istemci
                    // hâlâ bağlı olabilir. Bir frame'i düşür ama akışı
                    // öldürme.
                    log.error("SSE olayı gönderilemedi ({}):",
                            eventName, illegalState);
                    return false;
                }

                // Cause yok: emitter zaten tamamlanmış.
                cancel();
                return false;
            } catch (Exception unexpected) {
                // send() sözleşmesi gereği fırlatmaz: token callback'lerinden
                // çağrılıyor ve oradan fırlayan her şey sağlayıcı akışını
                // sarsar. Beklenmeyeni logla, akışı öldürme.
                log.error("SSE olayı gönderilirken beklenmeyen hata ({}):",
                        eventName, unexpected);
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
        completed.set(true);
        synchronized (lock) {
            emitter.complete();
        }
    }
}
