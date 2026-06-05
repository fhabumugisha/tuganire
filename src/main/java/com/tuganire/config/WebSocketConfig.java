package com.tuganire.config;

import com.tuganire.conversation.TranslationWebSocketHandler;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Registers {@link TranslationWebSocketHandler} at {@code /ws/conversation/{sessionId}}.
 *
 * <p>
 * The {@link SessionIdHandshakeInterceptor} captures the {@code sessionId} path segment from the handshake URL and
 * stores it as a WebSocket session attribute so the handler can use the business session ID rather than the technical
 * WebSocket session ID.
 *
 * <p>
 * Max binary message size is set to 512 KB to accommodate audio chunks streamed from mobile clients. The typical 16kHz
 * mono PCM chunk size is well under 64 KB; 512 KB gives comfortable headroom for larger chunks while preventing memory
 * pressure from oversized frames.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** Maximum binary message size in bytes (512 KB). Sized for 16kHz mono PCM audio chunks. */
    private static final int MAX_BINARY_MESSAGE_SIZE = 512 * 1024;

    private final TranslationWebSocketHandler translationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(translationWebSocketHandler, "/ws/conversation/{sessionId}")
                .addInterceptors(new SessionIdHandshakeInterceptor()).setAllowedOriginPatterns("*"); // fine-grained
                                                                                                     // CORS is handled
                                                                                                     // by
                                                                                                     // SecurityConfig /
                                                                                                     // CorsConfig
    }

    /**
     * Extracts the {@code sessionId} path variable from the handshake URL and stores it as a WebSocket session
     * attribute under the key {@code "sessionId"}.
     */
    static final class SessionIdHandshakeInterceptor implements HandshakeInterceptor {

        private static final String ATTR_SESSION_ID = "sessionId";

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String path = request.getURI().getPath();
            // path: /ws/conversation/{sessionId}
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                attributes.put(ATTR_SESSION_ID, path.substring(lastSlash + 1));
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                Exception exception) {
            // nothing to do after handshake
        }
    }
}
