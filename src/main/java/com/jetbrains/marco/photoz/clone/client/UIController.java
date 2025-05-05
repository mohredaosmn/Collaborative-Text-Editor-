package com.jetbrains.marco.photoz.clone.client;

import com.jetbrains.marco.photoz.clone.common.Message;
import com.jetbrains.marco.photoz.clone.common.JSONUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.scene.input.Clipboard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class UIController {
    @FXML private TextArea       textArea;
    @FXML private TextField      codeField, uidField;
    @FXML private Button         newBtn, joinBtn;
    @FXML private ListView<HBox> userList;

    private ClientConnection      conn;
    private CRDTTree              crdt            = new CRDTTree();
    private boolean               isRemoteUpdate  = false;
    private boolean               isReadOnly      = false;
    private String                currentSessionCode;
    private final Map<String,HBox> userEntries    = new HashMap<>();

    private static final String   CODE_CHARS      = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RNG         = new SecureRandom();

    private String generateSessionCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RNG.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    @FXML
    public void initialize() {
        newBtn .setOnAction(e -> onNew());
        joinBtn.setOnAction(e -> onConnect());

        // Handle typing (including space/newline)
        textArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (isRemoteUpdate || conn == null || isReadOnly) return;
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            char c = ch.charAt(0);
            if (Character.isISOControl(c) && c != '\n') return;
            e.consume();
            handleInsert(ch);
        });

        // Handle special keys: Enter, Backspace, Paste, Undo/Redo
        textArea.setOnKeyPressed(e -> {
            if (conn == null || isReadOnly) return;
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                handleInsert("\n");
            }
            else if (e.getCode() == KeyCode.BACK_SPACE) {
                handleBackspace(e);
            }
            else if (e.isControlDown() && e.getCode() == KeyCode.V) {
                e.consume();
                handlePaste();
            }
            else if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) onRedo(); else onUndo();
                e.consume();
            }
        });
    }

    @FXML public void onNew() {
        crdt = new CRDTTree();
        userList.getItems().clear();
        redraw(0);

        String editorCode   = generateSessionCode();
        String readOnlyCode = editorCode + "-RO";

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Session Codes");
        info.setHeaderText("Share these codes:");
        info.setContentText("Editor:    " + editorCode + "\nRead‑only: " + readOnlyCode);
        info.showAndWait();

        codeField.setText(editorCode);
        isReadOnly = false;
        joinSession(editorCode);
    }

    @FXML public void onConnect() {
        String raw = codeField.getText().trim();
        if (raw.isEmpty()) return;
        isReadOnly = raw.endsWith("-RO");
        joinSession(raw);
    }

    private void joinSession(String rawCode) {
        String base = isReadOnly
            ? rawCode.substring(0, rawCode.length() - 3)
            : rawCode;
        currentSessionCode = base;

        String uid = uidField.getText().trim();
        conn = new ClientConnection("ws://localhost:8080/ws/edit", this::onMessage);
        conn.connect(base, uid);

        textArea.setEditable(!isReadOnly);
        addUserToList(uid);

        textArea.caretPositionProperty().addListener((obs, o, n) -> {
            if (!isRemoteUpdate && conn != null) {
                int ln = calculateLine();
                updateUserLine(uid, ln);
                Message m = Message.cursorUpdate(base, uid, ln);
                conn.send(JSONUtils.toJson(m));
            }
        });
    }

    private void onMessage(String json) {
        Message m = JSONUtils.fromJson(json, Message.class);
        Platform.runLater(() -> handleIncoming(m));
    }

    private void handleIncoming(Message m) {
        String me = uidField.getText().trim();
        if (m.uid.equals(me)) return;
        switch (m.type) {
            case Message.JOIN:            addUserToList(m.uid);                break;
            case Message.LEAVE:           removeUserFromList(m.uid);           break;
            case Message.CURSOR_POSITION: updateUserLine(m.uid, m.cursorLine); break;
            case Message.INSERT:
                if (m.content!=null && m.parentId!=null)
                    crdt.insert(m.content, m.uid, m.clock, m.parentId);
                break;
            case Message.DELETE:
                if (m.targetId!=null) crdt.delete(m.targetId);
                break;
            case Message.UNDO:            crdt.undo();                         break;
            case Message.REDO:            crdt.redo();                         break;
            case Message.SPLIT:
                if (m.position>=0) crdt.splitAtPosition(m.position);
                break;
        }
        redraw(textArea.getCaretPosition());
    }

    private void addUserToList(String uid) {
        if (userEntries.containsKey(uid)) return;
        Label name = new Label(uid);
        if (uid.equals(uidField.getText().trim())) {
            name.setStyle("-fx-font-weight:bold; -fx-text-fill:#2a82da;");
        }
        Label line = new Label("(Line 1)");
        line.setStyle("-fx-text-fill:#666;");
        HBox row = new HBox(5, name, line);
        userList.getItems().add(row);
        userEntries.put(uid, row);
    }

    private void updateUserLine(String uid, int ln) {
        HBox row = userEntries.get(uid);
        if (row != null) {
            ((Label)row.getChildren().get(1)).setText("(Line " + (ln+1) + ")");
        }
    }

    private void removeUserFromList(String uid) {
        HBox row = userEntries.remove(uid);
        if (row != null) userList.getItems().remove(row);
    }

    private int calculateLine() {
        String t = textArea.getText(0, textArea.getCaretPosition());
        return t.split("\n", -1).length - 1;
    }

    /**
     * Always split first, then insert a single character.
     */
    private void handleInsert(String ch) {
        int pos    = textArea.getCaretPosition();
        String uid = uidField.getText().trim();
        String ts1 = String.valueOf(System.nanoTime());

        // 1) split at caret
        crdt.splitAtPosition(pos);
        conn.send(JSONUtils.toJson(
            Message.split(currentSessionCode, uid, ts1, pos)
        ));

        // 2) insert the character
        String parent = crdt.getParentIdForInsertAtPosition(pos);
        String ts2    = String.valueOf(System.nanoTime());
        crdt.insert(ch, uid, ts2, parent);
        conn.send(JSONUtils.toJson(
            Message.insert(currentSessionCode, uid, ts2, ch, parent)
        ));

        // 3) redraw
        redraw(pos + 1);
    }

    private void handleBackspace(KeyEvent e) {
        if (isReadOnly) { e.consume(); return; }
        int pos = textArea.getCaretPosition();
        if (pos <= 0) { e.consume(); return; }

        String uid = uidField.getText().trim();
        String ts1 = String.valueOf(System.nanoTime());
        crdt.splitAtPosition(pos);
        conn.send(JSONUtils.toJson(
            Message.split(currentSessionCode, uid, ts1, pos)
        ));

        String ts2 = String.valueOf(System.nanoTime());
        crdt.splitAtPosition(pos);
        conn.send(JSONUtils.toJson(
            Message.split(currentSessionCode, uid, ts2, pos-1)
        ));

        String id = crdt.getCharIdAtPosition(pos);
        if (id != null) {
            String ts3 = String.valueOf(System.nanoTime());
            crdt.delete(id);
            conn.send(JSONUtils.toJson(
                Message.delete(currentSessionCode, uid, ts3, id)
            ));
        }

        redraw(Math.max(0, pos));
        e.consume();
    }

    @FXML public void onUndo() {
        if (isReadOnly) return;
        crdt.undo();
        Message m = new Message();
        m.type        = Message.UNDO;
        m.uid         = uidField.getText().trim();
        m.sessionCode = currentSessionCode;
        conn.send(JSONUtils.toJson(m));
        redraw(textArea.getCaretPosition());
    }

    @FXML public void onRedo() {
        if (isReadOnly) return;
        crdt.redo();
        Message m = new Message();
        m.type        = Message.REDO;
        m.uid         = uidField.getText().trim();
        m.sessionCode = currentSessionCode;
        conn.send(JSONUtils.toJson(m));
        redraw(textArea.getCaretPosition());
    }

    @FXML public void onImport() throws IOException {
        File f = new FileChooser().showOpenDialog(null);
        if (f == null) return;
        String txt = Files.readString(f.toPath());
        crdt = new CRDTTree();
        for (char c : txt.toCharArray()) {
            if (!Character.isISOControl(c) || c == '\n') {
                crdt.insert(String.valueOf(c), "import",
                            String.valueOf(System.nanoTime()),
                            crdt.getRootId());
            }
        }
        redraw(0);
    }

    @FXML public void onExport() throws IOException {
        File f = new FileChooser().showSaveDialog(null);
        if (f == null) return;
        Files.writeString(f.toPath(), crdt.getDocument());
    }

    /**
     * Paste via CRDT: split once, then insert each character
     * using handleInsert’s split+insert logic.
     */
    private void handlePaste() {
        String clip = Clipboard.getSystemClipboard().getString();
        if (clip == null || clip.isEmpty()) return;
        for (char c : clip.toCharArray()) {
            handleInsert(String.valueOf(c));
        }
    }

    private void redraw(int caretPos) {
        isRemoteUpdate = true;
        textArea.setText(crdt.getDocument());
        caretPos = Math.max(0, Math.min(caretPos, textArea.getLength()));
        textArea.positionCaret(caretPos);
        isRemoteUpdate = false;
    }
}