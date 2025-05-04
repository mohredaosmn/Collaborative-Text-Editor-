package com.jetbrains.marco.photoz.clone.client;

import java.net.URI;
import java.util.function.Consumer;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import com.jetbrains.marco.photoz.clone.common.Message;

@ClientEndpoint
public class ClientConnection {
    private Session session;
    private final String serverUri;
    private final Consumer<Message> messageHandler;
    private String sessionCode;
    private String uid;

    /**
     * @param serverUri WebSocket server URL (e.g., "ws://localhost:8080/editor")
     * @param messageHandler Callback for incoming messages
     */
    public ClientConnection(String serverUri, Consumer<Message> messageHandler) {
        this.serverUri = serverUri;
        this.messageHandler = messageHandler;
    }

    public void connect(String sessionCode, String uid) {
        this.sessionCode = sessionCode;
        this.uid = uid;
        
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(serverUri));
        } catch (Exception e) {
            throw new RuntimeException("Connection failed: " + e.getMessage(), e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        sendMessage(Message.join(sessionCode, uid)); // Auto-send join message
    }

    @OnMessage
    public void onMessage(String jsonMessage) {
        try {
            Message message = JSONUtils.fromJson(jsonMessage, Message.class);
            messageHandler.accept(message);
        } catch (Exception e) {
            System.err.println("Failed to parse message: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        sendMessage(Message.leave(sessionCode, uid)); // Auto-send leave message
    }

    // Primary send method
    public void sendMessage(Message message) {
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(JSONUtils.toJson(message));
            } catch (Exception e) {
                System.err.println("Send failed: " + e.getMessage());
            }
        }
    }

    // Convenience method for cursor updates
    public void sendCursorPosition(int lineNumber) {
        sendMessage(Message.cursorUpdate(sessionCode, uid, lineNumber));
    }

    public void disconnect() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception e) {
            System.err.println("Disconnect error: " + e.getMessage());
        }
    }
}