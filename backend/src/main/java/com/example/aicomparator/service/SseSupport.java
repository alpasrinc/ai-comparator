package com.example.aicomparator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseSupport {

    private static final Logger log =
            LoggerFactory.getLogger(SseSupport.class);

    public void send(
            SseEmitter emitter,
            Object emitterLock,
            String eventName,
            Object data
    ) {
        synchronized (emitterLock) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(eventName)
                                .data(data, MediaType.APPLICATION_JSON)
                );
            } catch (Exception exception) {
                log.debug(
                        "SSE gönderimi başarısız (istemci muhtemelen "
                                + "bağlantıyı kapattı): {}",
                        exception.getMessage()
                );
            }
        }
    }
}
