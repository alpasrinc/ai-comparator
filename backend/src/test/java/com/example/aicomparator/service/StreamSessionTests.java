package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamSessionTests {

    @Test
    void sendsEventWhileClientIsConnected() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isTrue();
        assertThat(session.isCancelled()).isFalse();
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void cancelsItselfWhenSendFails() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("istemci gitti"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        assertThat(session.isCancelled()).isTrue();
    }

    @Test
    void doesNotSendAfterCancellation() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        StreamSession session = new StreamSession(emitter);

        session.cancel();
        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void abortIfCancelledThrowsOnlyAfterCancellation() {
        StreamSession session = new StreamSession(mock(SseEmitter.class));

        session.abortIfCancelled();

        session.cancel();
        assertThatThrownBy(session::abortIfCancelled)
                .isInstanceOf(StreamCancelledException.class);
    }

    @Test
    void completionWeInitiatedDoesNotCountAsCancellation() {
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> onCompletion =
                ArgumentCaptor.forClass(Runnable.class);
        StreamSession session = new StreamSession(emitter);
        verify(emitter).onCompletion(onCompletion.capture());

        session.complete();
        onCompletion.getValue().run();

        assertThat(session.isCancelled()).isFalse();
    }

    @Test
    void completionWeDidNotInitiateCancels() {
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> onCompletion =
                ArgumentCaptor.forClass(Runnable.class);
        StreamSession session = new StreamSession(emitter);
        verify(emitter).onCompletion(onCompletion.capture());

        onCompletion.getValue().run();

        assertThat(session.isCancelled()).isTrue();
    }

    @Test
    void serializationFailureDropsTheFrameWithoutCancelling() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException(
                        "Failed to send", new RuntimeException("jackson")))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        assertThat(session.isCancelled()).isFalse();
    }

    @Test
    void alreadyCompletedEmitterCancels() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("already set complete"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        assertThat(session.isCancelled()).isTrue();
    }

    @Test
    void unexpectedFailureDropsTheFrameWithoutCancelling() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new RuntimeException("beklenmeyen"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        StreamSession session = new StreamSession(emitter);

        boolean sent = session.send("token", "merhaba");

        assertThat(sent).isFalse();
        assertThat(session.isCancelled()).isFalse();
    }

    @Test
    void sendAfterOurOwnCompletionDoesNotReportClientCancellation() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("already set complete"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        StreamSession session = new StreamSession(emitter);

        session.complete();
        boolean sent = session.send("token", "geç kalan");

        assertThat(sent).isFalse();
        assertThat(session.isCancelled()).isFalse();
        assertThatThrownBy(session::abortIfCancelled)
                .isInstanceOf(StreamCancelledException.class);
    }
}
