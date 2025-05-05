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
    private final Consumer<String> onMessage;  // raw JSON consumer
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

    public void sendCursorPosition(int line) {
        Message m = Message.cursorUpdate(sessionCode, uid, line);
        send(JSONUtils.toJson(m));
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        Message join = Message.join(sessionCode, uid);
        send(JSONUtils.toJson(join));
    }

    @OnMessage
    public void onMsg(String text) {
        onMessage.accept(text);
    }

    @OnClose
    public void onClose(Session s) {
        Message leave = Message.leave(sessionCode, uid);
        send(JSONUtils.toJson(leave));
    }

    public void sendMessage(Message m) {
        send(JSONUtils.toJson(m));
    }

    public void send(String txt) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(txt);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}