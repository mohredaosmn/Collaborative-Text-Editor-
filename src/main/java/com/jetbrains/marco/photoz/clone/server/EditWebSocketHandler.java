package com.jetbrains.marco.photoz.clone.server;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import com.jetbrains.marco.photoz.clone.common.Message;

public class EditWebSocketHandler extends TextWebSocketHandler {
    private final SessionManager sessionManager = SessionManager.getInstance();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println(">>> Connection established: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Message msg = JSONUtils.fromJson(message.getPayload(), Message.class);
            if (msg == null) {
                System.out.println(">>> Failed to parse message");
                return;
            }

            switch (msg.type) {
                case Message.JOIN:
                    handleJoin(session, msg);
                    break;
                    
                case Message.LEAVE:
                    handleLeave(msg);
                    break;
                    
                case Message.INSERT:
                case Message.DELETE:
                case Message.UNDO:
                case Message.REDO:
                    handleEditOperation(msg);
                    break;
                    
                case Message.CURSOR_POSITION:
                    handleCursorPosition(session, msg);
                    break;
                    
                default:
                    System.out.println(">>> Unknown message type: " + msg.type);
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    private void handleJoin(WebSocketSession session, Message msg) {
        sessionManager.registerUserSession(msg.sessionCode, msg.uid, session);

        // Notify new user about existing participants
        sessionManager.getSessionParticipants(msg.sessionCode).forEach(existingUid -> {
            if (!existingUid.equals(msg.uid)) {
                sendMessage(session, Message.join(msg.sessionCode, existingUid));
            }
        });

        // Notify others about new participant (excluding self)
        sessionManager.broadcastMessage(msg.sessionCode, 
            Message.join(msg.sessionCode, msg.uid), 
            session);
        
        System.out.println(">>> " + msg.uid + " joined session: " + msg.sessionCode);
    }

    private void handleLeave(Message msg) {
        sessionManager.broadcastMessage(msg.sessionCode, 
            Message.leave(msg.sessionCode, msg.uid), 
            null);
    }

    private void handleEditOperation(Message msg) {
        if (msg.sessionCode == null) {
            System.out.println(">>> Missing session code in edit operation");
            return;
        }
        sessionManager.broadcastMessage(msg.sessionCode, msg, null);
    }

    private void handleCursorPosition(WebSocketSession session, Message msg) {
        if (msg.sessionCode == null || msg.uid == null) return;
        sessionManager.broadcastMessage(msg.sessionCode, msg, session);
    }

    private void sendMessage(WebSocketSession session, Message msg) {
        try {
            session.sendMessage(new TextMessage(JSONUtils.toJson(msg)));
        } catch (Exception e) {
            System.err.println("Failed to send message to " + session.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String uid = sessionManager.getUserIdForSession(session);
        String sessionCode = sessionManager.getSessionCodeForWebSocket(session);
        
        if (uid != null && sessionCode != null) {
            sessionManager.broadcastMessage(sessionCode, 
                Message.leave(sessionCode, uid), 
                null);
        }
        
        sessionManager.unregisterSession(session);
        System.out.println(">>> Connection closed: " + session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println(">>> Transport error for " + session.getId() + ": " + exception.getMessage());
    }
}