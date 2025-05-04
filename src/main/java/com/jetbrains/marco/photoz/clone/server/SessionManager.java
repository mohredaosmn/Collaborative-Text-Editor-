package com.jetbrains.marco.photoz.clone.server;

import org.springframework.web.socket.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
  private static final SessionManager instance = new SessionManager();

  public static SessionManager getInstance() {
    return instance;
  }

  private record Session(
      String editorCode, String viewerCode,
      Map<String, WebSocketSession> users) {
  }

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  public void addUserToSession(String code, String uid, WebSocketSession ws) {
    sessions
        .computeIfAbsent(code, c -> new Session(code + "E", code + "V", new ConcurrentHashMap<>())).users.put(uid, ws);
  }

  public void broadcast(String code, String payload, WebSocketSession origin) {
    Session s = sessions.get(code);
    if (s == null)
      return;

    s.users.values().forEach(ws -> {
      if (ws.isOpen()) {
        try {
          ws.sendMessage(new TextMessage(payload));
        } catch (Exception ignored) {
        }
      }
    });
  }

  public void removeSession(WebSocketSession ws) {
    for (Session s : sessions.values()) {
      s.users.values().removeIf(u -> u.getId().equals(ws.getId()));
    }
  }

  public Set<String> getUsersInSession(String code) {
    Session s = sessions.get(code);
    if (s == null)
      return Set.of();
    return s.users.keySet();
  }
}
