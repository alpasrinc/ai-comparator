package com.example.aicomparator.dto;

/**
 * Kullanıcının seçtiği yanıt yoğunluğu. Hem çıkış token limitini ölçekler
 * hem de modele kısa/detaylı yanıt vermesi için bir yönerge ekler.
 */
public enum ResponseIntensity {

    LOW(0.25, "Kısa ve öz bir yanıt ver; yalnızca en kritik noktalara değin."),
    MEDIUM(1.0, ""),
    HIGH(2.0, "Kapsamlı ve detaylı bir yanıt ver; gerekçelerini ve "
            + "örneklerini açıklayarak yaz.");

    private static final long MIN_TOKENS = 256L;

    private final double tokenFactor;
    private final String directive;

    ResponseIntensity(double tokenFactor, String directive) {
        this.tokenFactor = tokenFactor;
        this.directive = directive;
    }

    /**
     * Sağlayıcının temel çıkış limitini yoğunluğa göre ölçekler.
     */
    public long scaleTokens(long baseLimit) {
        return Math.max(MIN_TOKENS, Math.round(baseLimit * tokenFactor));
    }

    /**
     * Yoğunluk yönergesini kullanıcı mesajının başına ekler.
     */
    public String applyTo(String userMessage) {
        return directive.isBlank()
                ? userMessage
                : directive + "\n\n" + userMessage;
    }

    public static ResponseIntensity orDefault(ResponseIntensity value) {
        return value == null ? MEDIUM : value;
    }
}
