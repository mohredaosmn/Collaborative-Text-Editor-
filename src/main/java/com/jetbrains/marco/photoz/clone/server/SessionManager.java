package com.jetbrains.marco.photoz.clone.server;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import com.jetbrains.marco.photoz.clone.common.Message;

public class SessionManager {
    private static final SessionManager instance = new SessionManager();

    private record SessionData(
        String editorCode,
        String viewerCode,
        Map<String, WebSocketSession> userSessions,
        Map<String, Integer> cursorPositions
    ) {}

    private final Map<String, SessionData> activeSessions = new ConcurrentHashMap<>();

    public static SessionManager getInstance() {
        return instance;
    }

    public void registerUserSession(String sessionCode, String userId, WebSocketSession session) {
        activeSessions.computeIfAbsent(sessionCode, code -> 
            new SessionData(
                code + "-editor", 
                code + "-viewer",
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>()
            )
        ).userSessions.put(userId, session);
    }

    public void broadcastMessage(String sessionCode, Message message, WebSocketSession excludeSession) {
        SessionData session = activeSessions.get(sessionCode);
        if (session == null) return;

        // Update cursor position if applicable
        if (Message.CURSOR_POSITION.equals(message.type)) {
            session.cursorPositions.put(message.uid, message.cursorLine);
        }

        String jsonPayload = JSONUtils.toJson(message);
        session.userSessions.forEach((userId, ws) -> {
            if (ws.isOpen() && !ws.equals(excludeSession)) {
                try {
                    ws.sendMessage(new TextMessage(jsonPayload));
                } catch (Exception e) {
                    System.err.printf("Failed to send to user %s: %s%n", userId, e.getMessage());
                }
            }
        });
    }

    public void unregisterSession(WebSocketSession session) {
        activeSessions.values().forEach(sessionData -> {
            sessionData.userSessions.values().removeIf(ws -> ws.getId().equals(session.getId()));
            sessionData.cursorPositions.keySet().retainAll(sessionData.userSessions.keySet());
        });
    }

    public Set<String> getSessionParticipants(String sessionCode) {
        SessionData session = activeSessions.get(sessionCode);
        return session != null ? session.userSessions.keySet() : Collections.emptySet();
    }

    public String getUserIdForSession(WebSocketSession session) {
        return activeSessions.values().stream()
            .flatMap(sd -> sd.userSessions.entrySet().stream())
            .filter(entry -> entry.getValue().getId().equals(session.getId()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    public String getSessionCodeForWebSocket(WebSocketSession session) {
        return activeSessions.entrySet().stream()
            .filter(entry -> entry.getValue().userSessions.containsValue(session))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    public Integer getCursorPosition(String sessionCode, String userId) {
        SessionData session = activeSessions.get(sessionCode);
        return session != null ? session.cursorPositions.get(userId) : null;
    }
}