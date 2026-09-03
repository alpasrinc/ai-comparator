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
