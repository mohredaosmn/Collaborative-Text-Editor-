package com.jetbrains.marco.photoz.clone.server;

import com.jetbrains.marco.photoz.clone.common.Message;
import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class EditWebSocketHandler extends TextWebSocketHandler {
  private final SessionManager sessions = SessionManager.getInstance();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println(">>> WebSocket connection established: " + session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage msg) throws Exception {
    System.out.println(">>> Received: " + msg.getPayload());

    Message m = JSONUtils.fromJson(msg.getPayload(), Message.class);

    switch (m.type) {
      case "join":
        sessions.addUserToSession(m.sessionCode, m.uid, session);

        // Send current users to new client
        for (String existingUid : sessions.getUsersInSession(m.sessionCode)) {
          Message joinMsg = new Message();
          joinMsg.type = "join";
          joinMsg.uid = existingUid;
          session.sendMessage(new TextMessage(JSONUtils.toJson(joinMsg)));
        }

        // Notify others that new user joined
        Message joinConfirm = new Message();
        joinConfirm.type = "join";
        joinConfirm.uid = m.uid;
        sessions.broadcast(m.sessionCode, JSONUtils.toJson(joinConfirm), null);

        System.out.println(">>> " + m.uid + " joined session: " + m.sessionCode);
        break;

      case "insert":
      case "delete":
      case "cursor":
        if (m.sessionCode == null) {
          System.out.println(">>> ERROR: sessionCode is null in message: " + JSONUtils.toJson(m));
          return;
        }
        sessions.broadcast(m.sessionCode, JSONUtils.toJson(m), null);
        break;

      default:
        System.out.println(">>> Unknown message type: " + m.type);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    System.out.println(">>> WebSocket connection closed: " + session.getId());
    sessions.removeSession(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    System.out.println(">>> WebSocket transport error: " + exception.getMessage());
  }
}
