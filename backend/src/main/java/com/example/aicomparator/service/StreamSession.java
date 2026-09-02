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
