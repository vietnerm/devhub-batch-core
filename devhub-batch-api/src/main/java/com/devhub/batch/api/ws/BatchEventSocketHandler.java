package com.devhub.batch.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out hub for realtime events: log executions, task changes, worker
 * snapshots. Payload shape: {"type":"log|task|task_removed|workers", "payload":..., "occurredAt":...}.
 */
@Component
public class BatchEventSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchEventSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper;

    public BatchEventSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("event=ws.connected id={} total={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("event=ws.disconnected id={} total={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client messages are ignored (keepalive pings allowed).
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void broadcast(String type, Object payload) {
        if (sessions.isEmpty()) return;
        String json;
        try {
            json = mapper.writeValueAsString(Map.of(
                    "type", type,
                    "payload", payload,
                    "occurredAt", LocalDateTime.now().toString()));
        } catch (IOException e) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } else {
                    sessions.remove(session);
                }
            } catch (IOException | IllegalStateException dead) {
                sessions.remove(session);
            }
        }
    }
}
