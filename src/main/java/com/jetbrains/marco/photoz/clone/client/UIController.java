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
        // 1) Catch every typed character (including space & Enter) in KEY_TYPED
        textArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (isRemoteUpdate || conn == null) return;

            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            char c = ch.charAt(0);

            // Allow everything except ISO-control (we want space and newline!)
            if (Character.isISOControl(c) && c != '\n') return;

            e.consume();  // take over default insertion

            if (c == '\n') {
                handleEnter();      // delegate newline
            } else {
                handleInsert(ch);   // delegate every other printable char
            }
        });

        // 2) Handle only control keys here
        textArea.setOnKeyPressed(e -> {
            if (conn == null) return;

            // Undo/Redo
            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) onRedo();
                else onUndo();
                e.consume();
                return;
            }

            // Backspace
            if (e.getCode() == KeyCode.BACK_SPACE) {
                handleBackspace(e);
            }
            // (no SPACE or ENTER here—everything’s in KEY_TYPED)
        });
    }

// --- NEW: handle Enter presses ---
    private void handleEnter() {
        int caret    = textArea.getCaretPosition();
        String uid   = uidField.getText().trim();
        String code  = codeField.getText().trim();
        String clock = String.valueOf(System.nanoTime());
        String parentId = crdt.getParentIdForInsertAtPosition(caret);

        // insert newline into CRDT
        crdt.insert("\n", uid, clock, parentId);
        // broadcast
        Message m = new Message("insert", uid, code, clock, "\n", parentId);
        conn.send(JSONUtils.toJson(m));

        redraw(caret + 1);
    }

// --- existing delegate for all other chars (letters, digits, spaces, punctuation) ---
    private void handleInsert(String ch) {
        int caret    = textArea.getCaretPosition();
        String uid   = uidField.getText().trim();
        String code  = codeField.getText().trim();
        String clock = String.valueOf(System.nanoTime());
        String parentId = crdt.getParentIdForInsertAtPosition(caret);

        crdt.insert(ch, uid, clock, parentId);
        Message m = new Message("insert", uid, code, clock, ch, parentId);
        conn.send(JSONUtils.toJson(m));

        redraw(caret + 1);
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

    private void handleSpace(KeyEvent e) {
        int caret = textArea.getCaretPosition();
        if (caret < 0) {
            e.consume();
            return;
        }

    // 1) Figure out where to attach the space
    String parentId = crdt.getParentIdForInsertAtPosition(caret);
    String clock    = String.valueOf(System.nanoTime());
    String uid      = uidField.getText().trim();
    String code     = codeField.getText().trim();

    // 2) Insert the space into the CRDT
    crdt.insert(" ", uid, clock, parentId);

    // 3) Broadcast the insert
    Message m = new Message();
    m.type        = "insert";
    m.uid         = uid;
    m.sessionCode = code;
    m.clock       = clock;
    m.value       = " ";
    m.parentId    = parentId;
    conn.send(JSONUtils.toJson(m));

    // 4) Consume and redraw one position over
    e.consume();
    redraw(caret + 1);
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

