package com.jetbrains.marco.photoz.clone.client;

import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import com.jetbrains.marco.photoz.clone.client.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class UIController {
    @FXML public TextArea textArea;
    @FXML public TextField codeField, uidField;
    @FXML public ListView<String> userList;

    private ClientConnection conn;
    private CRDTTree crdt = new CRDTTree();
    private boolean isRemoteUpdate = false;

    @FXML
    public void onConnect() {
        String code = codeField.getText().trim();
        String uid = uidField.getText().trim();
        conn = new ClientConnection("ws://localhost:8080/ws/edit", this::onMessage);
        conn.connect(code, uid);
    }

    @FXML
    public void onImport() throws IOException {
        File f = new FileChooser().showOpenDialog(null);
        if (f == null) return;
        String txt = Files.readString(f.toPath());
        crdt = new CRDTTree();
        for (char c : txt.toCharArray()) {
            if (!Character.isISOControl(c) || c == '\n') {
                crdt.insert(String.valueOf(c), "import", String.valueOf(System.nanoTime()), "0-0");
            }
        }
        redraw(0);
    }

    @FXML
    public void onExport() throws IOException {
        File f = new FileChooser().showSaveDialog(null);
        if (f == null) return;
        Files.writeString(f.toPath(), crdt.getDocument());
    }

    @FXML
    public void onUndo() {
        crdt.undo();
        broadcastControl("undo");
        redraw(textArea.getCaretPosition());
    }

    @FXML
    public void onRedo() {
        crdt.redo();
        broadcastControl("redo");
        redraw(textArea.getCaretPosition());
    }

    private void onMessage(String payload) {
        Message m = JSONUtils.fromJson(payload, Message.class);
        if (m == null || m.uid.equals(uidField.getText().trim())) return;

        Platform.runLater(() -> {
            switch (m.type) {
                case "insert":
                    if (m.value != null && m.parentId != null) {
                        crdt.insert(m.value, m.uid, m.clock, m.parentId);
                    }
                    break;
                case "delete":
                    if (m.targetId != null) {
                        crdt.delete(m.targetId);
                    }
                    break;
                case "undo":
                    crdt.undo();
                    break;
                case "redo":
                    crdt.redo();
                    break;
                case "join":
                    if (m.uid != null && !userList.getItems().contains(m.uid)) {
                        userList.getItems().add(m.uid);
                    }
                    break;
                case "split":
                    if (m.position >= 0) {
                        crdt.splitAtPosition(m.position);
                    }
                    break;
            }
            redraw(textArea.getCaretPosition());
        });
    }

    @FXML
    public void initialize() {
        textArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (isRemoteUpdate || conn == null) return;
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            char c = ch.charAt(0);
            if (Character.isISOControl(c)) return;

            int caret = textArea.getCaretPosition();
            String parentId = crdt.getParentIdForInsertAtPosition(caret);
            String clock = String.valueOf(System.nanoTime());
            String uid = uidField.getText().trim();

            crdt.insert(ch, uid, clock, parentId);
            Message m = new Message("insert", uid, codeField.getText().trim(), clock, ch, parentId);
            conn.send(JSONUtils.toJson(m));

            e.consume();
            redraw(caret + 1);
        });

        textArea.setOnKeyPressed(e -> {
            if (conn == null) return;

            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) onRedo();
                else onUndo();
                e.consume();
                return;
            }

            if (e.getCode() == KeyCode.BACK_SPACE) {
                handleBackspace(e);
                return;
            }

            if (e.getCode() == KeyCode.ENTER) {
                handleEnter(e);
                return;
            }
        });
    }
     // Updated backspace logic (working version)
     private void handleBackspace(KeyEvent e) {
        int caret = textArea.getCaretPosition();
        if (caret <= 0) {
            e.consume();
            return;
        }

        // Split at the current caret to separate nodes
        crdt.splitAtPosition(caret);
        Message splitMsg1 = new Message("split", uidField.getText().trim(), codeField.getText().trim(),
                                      String.valueOf(System.nanoTime()), caret);
        conn.send(JSONUtils.toJson(splitMsg1));

        // Split again at the current caret to isolate the character
        crdt.splitAtPosition(caret);
        Message splitMsg2 = new Message("split", uidField.getText().trim(), codeField.getText().trim(),
                                      String.valueOf(System.nanoTime()), caret - 1);
        conn.send(JSONUtils.toJson(splitMsg2));

        // Delete the single character node
        String id = crdt.getCharIdAtPosition(caret);
        if (id != null) {
            crdt.delete(id);
            Message deleteMsg = new Message("delete", uidField.getText().trim(), codeField.getText().trim(),
                                          String.valueOf(System.nanoTime()), id);
            conn.send(JSONUtils.toJson(deleteMsg));
        }

        redraw(Math.max(0, caret));
        e.consume();
    }


    private void handleEnter(KeyEvent e) {
        int caret = textArea.getCaretPosition();
        crdt.splitAtPosition(caret);
        
        Message m = new Message("split", uidField.getText().trim(), codeField.getText().trim(),
                              String.valueOf(System.nanoTime()), caret);
        conn.send(JSONUtils.toJson(m));

        redraw(caret + 1);
        e.consume();
    }

    private void broadcastControl(String type) {
        if (conn == null) return;
        Message m = new Message(type, uidField.getText().trim(), codeField.getText().trim());
        conn.send(JSONUtils.toJson(m));
    }

    public void redraw(int caretPos) {
        isRemoteUpdate = true;
        textArea.setText(crdt.getDocument());
        caretPos = Math.max(0, Math.min(caretPos, textArea.getLength()));
        textArea.positionCaret(caretPos);
        isRemoteUpdate = false;
    }
}