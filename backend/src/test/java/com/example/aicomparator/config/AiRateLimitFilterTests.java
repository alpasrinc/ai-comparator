package com.example.aicomparator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AiRateLimitFilterTests {

    @Test
    void shouldAllowRequestsWithinCapacity() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(
                2, 60, List.of("/api/chat/"));

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = chatRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void shouldRejectRequestsBeyondCapacity() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(
                2, 60, List.of("/api/chat/"));

        for (int i = 0; i < 2; i++) {
            filter.doFilter(
                    chatRequest(),
                    new MockHttpServletResponse(),
                    new MockFilterChain()
            );
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();

        filter.doFilter(chatRequest(), blockedResponse, blockedChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedChain.getRequest()).isNull();
    }

    @Test
    void shouldIgnoreNonChatEndpoints() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(
                1, 60, List.of("/api/chat/"));

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    "/api/conversations"
            );
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void shouldProtectConversationDocumentEndpoints() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(
                1, 60, List.of("/api/chat/"));

        MockHttpServletRequest first = new MockHttpServletRequest(
                "POST", "/api/conversations/42/documents");
        first.setRemoteAddr("127.0.0.1");
        filter.doFilter(first, new MockHttpServletResponse(),
                new MockFilterChain());

        MockHttpServletRequest second = new MockHttpServletRequest(
                "POST", "/api/conversations/42/documents");
        second.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(second, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest chatRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/chat/compare"
        );
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
