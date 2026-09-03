package com.example.aicomparator.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IP başına token-bucket rate limiter. Ücretli çağrı tetikleyen uçları korur:
 * yapılandırmadaki /api/chat/** önekleri ve belge yükleme uçları
 * (/api/conversations/*&#47;documents**, embedding çağrısı üretir).
 * Ekstra bir bağımlılık gerektirmez.
 */
@Component
public class AiRateLimitFilter extends HttpFilter {

    private final int capacity;
    private final long refillIntervalMillis;
    private final List<String> protectedPathPrefixes;
    private final ConcurrentHashMap<String, TokenBucket> buckets =
            new ConcurrentHashMap<>();

    public AiRateLimitFilter(
            @Value("${ai.rate-limit.capacity:20}") int capacity,
            @Value("${ai.rate-limit.refill-interval-seconds:3}")
            long refillIntervalSeconds,
            @Value("${ai.rate-limit.protected-paths}")
            List<String> protectedPathPrefixes
    ) {
        this.capacity = capacity;
        this.refillIntervalMillis = refillIntervalSeconds * 1000;
        this.protectedPathPrefixes = List.copyOf(protectedPathPrefixes);
    }

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        String uri = request.getRequestURI();
        boolean protectedPath = protectedPathPrefixes.stream()
                        .anyMatch(uri::startsWith)
                || (uri.startsWith("/api/conversations/")
                        && uri.contains("/documents"));

        if (!protectedPath) {
            chain.doFilter(request, response);
            return;
        }

        TokenBucket bucket = buckets.computeIfAbsent(
                clientKey(request),
                key -> new TokenBucket(capacity)
        );

        if (!bucket.tryConsume(capacity, refillIntervalMillis)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Çok fazla istek gönderildi. "
                            + "Lütfen birazdan tekrar deneyin.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static final class TokenBucket {
        private double tokens;
        private long lastRefillTimestamp;

        TokenBucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(
                int capacity,
                long refillIntervalMillis
        ) {
            refill(capacity, refillIntervalMillis);

            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }

            return false;
        }

        private void refill(int capacity, long refillIntervalMillis) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;

            if (elapsed <= 0) {
                return;
            }

            double tokensToAdd = (double) elapsed / refillIntervalMillis;

            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
