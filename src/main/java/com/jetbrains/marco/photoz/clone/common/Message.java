package com.jetbrains.marco.photoz.clone.common;

/**
 * Unified message format for both CRDT operations and cursor tracking
 */
public class Message {
    // Message types
    public static final String JOIN = "join";
    public static final String LEAVE = "leave";
    public static final String EDIT = "edit";
    public static final String CURSOR_POSITION = "cursor_position";
    public static final String INSERT = "insert";
    public static final String DELETE = "delete";
    public static final String UNDO = "undo";
    public static final String REDO = "redo";
    public static final String SPLIT = "split";

    // Core fields
    public String type;
    public String sessionCode;
    public String uid;
    public long timestamp = System.currentTimeMillis();
    
    // Cursor tracking
    public int cursorLine;
    
    // CRDT operations
    public String content;  // Generic content (replaces 'value')
    public String clock;
    public String parentId;
    public String targetId;
    public int position;

    // Empty constructor for JSON
    public Message() {}

    // --------- Factory Methods --------- //
    
    public static Message join(String sessionCode, String uid) {
        Message msg = new Message();
        msg.type = JOIN;
        msg.sessionCode = sessionCode;
        msg.uid = uid;
        return msg;
    }

    public static Message leave(String sessionCode, String uid) {
        Message msg = new Message();
        msg.type = LEAVE;
        msg.sessionCode = sessionCode;
        msg.uid = uid;
        return msg;
    }

    public static Message cursorUpdate(String sessionCode, String uid, int line) {
        Message msg = new Message();
        msg.type = CURSOR_POSITION;
        msg.sessionCode = sessionCode;
        msg.uid = uid;
        msg.cursorLine = line;
        return msg;
    }

    public static Message insert(String sessionCode, String uid, String clock, 
                               String content, String parentId) {
        Message msg = new Message();
        msg.type = INSERT;
        msg.sessionCode = sessionCode;
        msg.uid = uid;
        msg.clock = clock;
        msg.content = content;
        msg.parentId = parentId;
        return msg;
    }

    public static Message delete(String sessionCode, String uid, String clock, String targetId) {
        Message msg = new Message();
        msg.type = DELETE;
        msg.sessionCode = sessionCode;
        msg.uid = uid;
        msg.clock = clock;
        msg.targetId = targetId;
        return msg;
    }

    // --------- Utility Methods --------- //
    
    public boolean isType(String type) {
        return this.type != null && this.type.equals(type);
    }

    public boolean isCrdtOperation() {
        return isType(INSERT) || isType(DELETE) || isType(SPLIT) || 
               isType(UNDO) || isType(REDO);
    }

    @Override
    public String toString() {
        return String.format("Message[%s, uid=%s, session=%s] {content=%s, line=%d}", 
            type, uid, sessionCode, 
            (content != null ? "'" + content + "'" : "null"), 
            cursorLine);
    }
}