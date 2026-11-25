package com.devhub.batch.api.config;

import com.devhub.batch.api.ws.BatchEventSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Realtime event stream. Registered at both /ws/events (direct deployments)
 * and / (sandbox gateway forwards WebSocket upgrades carrying XTransformPort
 * to this server with the original path preserved — both shapes land here).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BatchEventSocketHandler handler;

    public WebSocketConfig(BatchEventSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/events", "/").setAllowedOriginPatterns("*");
    }
}
