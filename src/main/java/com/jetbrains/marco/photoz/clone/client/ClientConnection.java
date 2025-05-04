package com.jetbrains.marco.photoz.clone.client;

import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import com.jetbrains.marco.photoz.clone.common.Message;

import javax.websocket.*;
import java.net.URI;
import java.util.function.Consumer;

@ClientEndpoint
public class ClientConnection {
    private Session session;
    private final String uri;
    private final Consumer<String> onMessage;

    private String sessionCode;
    private String uid;

    public ClientConnection(String uri, Consumer<String> onMessage) {
        this.uri = uri;
        this.onMessage = onMessage;
    }

    public void connect(String sessionCode, String uid) {
        this.sessionCode = sessionCode;
        this.uid = uid;

        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println(">>> WebSocket connection opened");

        // Send join message after connection established
        Message joinMsg = new Message();
        joinMsg.type = "join";
        joinMsg.sessionCode = sessionCode;
        joinMsg.uid = uid;

        send(JSONUtils.toJson(joinMsg));
    }

    @OnMessage
    public void onMsg(String msg) {
        onMessage.accept(msg);
    }

    @OnClose
    public void onClose(Session s) {
        System.out.println(">>> WebSocket connection closed");
    }

    public void send(String text) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
