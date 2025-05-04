package com.jetbrains.marco.photoz.clone.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.jetbrains.marco.photoz.clone.common.Message;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

public class UIController {
    @FXML public TextArea textArea;
    @FXML public TextField codeField, uidField;
    @FXML public ListView<HBox> userList;
    
    private ClientConnection conn;
    private CRDTTree crdt = new CRDTTree();
    private boolean isRemoteUpdate = false;
    private final Map<String, HBox> userEntries = new HashMap<>();

    // --- code‐generation support ---
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RNG = new SecureRandom();
    /** Builds a random 6‑char alphanumeric session code. */
    private String generateSessionCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RNG.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /** Join an existing session. */
    @FXML
    public void onConnect() {
        String code = codeField.getText().trim();
        String uid  = uidField.getText().trim();
        conn = new ClientConnection("ws://localhost:8080/ws/edit", this::handleIncomingMessage);
        conn.connect(code, uid);

        // Broadcast caret‐line on move
        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (!isRemoteUpdate && conn != null) {
                int line = calculateCurrentLine();
                conn.sendCursorPosition(line);
            }
        });
    }

    /** Create a new blank document and start a fresh session. */
    @FXML
    public void onNew() {
        // reset CRDT & UI
        crdt = new CRDTTree();
        userList.getItems().clear();
        redraw(0);

        // generate & show new code
        String newCode = generateSessionCode();
        codeField.setText(newCode);

        // then join it
        onConnect();
    }

    private void handleIncomingMessage(Message msg) {
        Platform.runLater(() -> {
            if (msg.uid.equals(uidField.getText().trim())) return;

            switch (msg.type) {
                case Message.JOIN:
                    addUserToList(msg.uid);
                    break;
                case Message.LEAVE:
                    removeUserFromList(msg.uid);
                    break;
                case Message.CURSOR_POSITION:
                    updateUserLine(msg.uid, msg.cursorLine);
                    break;
                case Message.INSERT:
                    if (msg.content != null && msg.parentId != null) {
                        crdt.insert(msg.content, msg.uid, msg.clock, msg.parentId);
                    }
                    break;
                case Message.DELETE:
                    if (msg.targetId != null) {
                        crdt.delete(msg.targetId);
                    }
                    break;
                case Message.UNDO:
                    crdt.undo();
                    break;
                case Message.REDO:
                    crdt.redo();
                    break;
                case Message.SPLIT:
                    if (msg.position >= 0) {
                        crdt.splitAtPosition(msg.position);
                    }
                    break;
            }
            redraw(textArea.getCaretPosition());
        });
    }

    private void addUserToList(String uid) {
        if (!userEntries.containsKey(uid)) {
            Label nameLabel = new Label(uid);
            Label lineLabel = new Label("(Line 1)");
            lineLabel.setStyle("-fx-text-fill: #666;");
            HBox container = new HBox(5, nameLabel, lineLabel);
            userList.getItems().add(container);
            userEntries.put(uid, container);
        }
    }

    private void updateUserLine(String uid, int line) {
        HBox container = userEntries.get(uid);
        if (container != null) {
            Label lineLabel = (Label) container.getChildren().get(1);
            lineLabel.setText("(Line " + (line + 1) + ")");
        }
    }

    private void removeUserFromList(String uid) {
        HBox container = userEntries.remove(uid);
        if (container != null) {
            userList.getItems().remove(container);
        }
    }

    private int calculateCurrentLine() {
        String text = textArea.getText(0, textArea.getCaretPosition());
        return text.split("\n", -1).length - 1;
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
        broadcastControl(Message.UNDO);
        redraw(textArea.getCaretPosition());
    }

    @FXML
    public void onRedo() {
        crdt.redo();
        broadcastControl(Message.REDO);
        redraw(textArea.getCaretPosition());
    }

    @FXML
    public void initialize() {
        textArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (isRemoteUpdate || conn == null) return;
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            char c = ch.charAt(0);
            if (Character.isISOControl(c) && c != '\n') return;
            e.consume();
            if (c == '\n') handleEnter();
            else          handleInsert(ch);
        });

        textArea.setOnKeyPressed(e -> {
            if (conn == null) return;
            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) onRedo();
                else                  onUndo();
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.BACK_SPACE) {
                handleBackspace(e);
            }
        });
    }

    private void handleEnter() {
        int caret    = textArea.getCaretPosition();
        String uid   = uidField.getText().trim();
        String code  = codeField.getText().trim();
        String clock = String.valueOf(System.nanoTime());
        String parentId = crdt.getParentIdForInsertAtPosition(caret);

        // insert newline
        crdt.insert("\n", uid, clock, parentId);
        Message msg = Message.insert(code, uid, clock, "\n", parentId);
        conn.sendMessage(msg);
        redraw(caret + 1);
    }

    private void handleInsert(String ch) {
        int caret    = textArea.getCaretPosition();
        String parentId = crdt.getParentIdForInsertAtPosition(caret);
        Message msg = Message.insert(
            codeField.getText().trim(),
            uidField.getText().trim(),
            String.valueOf(System.nanoTime()),
            ch,
            parentId
        );
        crdt.insert(ch, msg.uid, msg.clock, msg.parentId);
        conn.sendMessage(msg);
        redraw(caret + 1);
    }

    private void handleBackspace(KeyEvent e) {
        int caret = textArea.getCaretPosition();
        if (caret <= 0) { e.consume(); return; }

        // split1
        crdt.splitAtPosition(caret);
        Message split1 = Message.split(
            codeField.getText().trim(),
            uidField.getText().trim(),
            String.valueOf(System.nanoTime()),
            caret
        );
        conn.sendMessage(split1);

        // split2
        crdt.splitAtPosition(caret);
        Message split2 = Message.split(
            codeField.getText().trim(),
            uidField.getText().trim(),
            String.valueOf(System.nanoTime()),
            caret - 1
        );
        conn.sendMessage(split2);

        // delete
        String id = crdt.getCharIdAtPosition(caret);
        if (id != null) {
            crdt.delete(id);
            Message deleteMsg = Message.delete(
                codeField.getText().trim(),
                uidField.getText().trim(),
                String.valueOf(System.nanoTime()),
                id
            );
            conn.sendMessage(deleteMsg);
        }

        redraw(Math.max(0, caret));
        e.consume();
    }

    private void broadcastControl(String type) {
        if (conn == null) return;
        Message msg = new Message();
        msg.type = type;
        msg.sessionCode = codeField.getText().trim();
        msg.uid         = uidField.getText().trim();
        conn.sendMessage(msg);
    }

    public void redraw(int caretPos) {
        isRemoteUpdate = true;
        textArea.setText(crdt.getDocument());
        caretPos = Math.max(0, Math.min(caretPos, textArea.getLength()));
        textArea.positionCaret(caretPos);
        isRemoteUpdate = false;
    }
}