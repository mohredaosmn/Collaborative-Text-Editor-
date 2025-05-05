package com.jetbrains.marco.photoz.clone.common;

/**
 * Unified message format for CRDT ops, cursor tracking, and session events.
 */
public class Message {
    public static final String JOIN             = "join";
    public static final String LEAVE            = "leave";
    public static final String CURSOR_POSITION  = "cursor_position";
    public static final String INSERT           = "insert";
    public static final String DELETE           = "delete";
    public static final String UNDO             = "undo";
    public static final String REDO             = "redo";
    public static final String SPLIT            = "split";

    public String type;
    public String sessionCode;
    public String uid;
    public long   timestamp = System.currentTimeMillis();
    public int    cursorLine;

    public String content;
    public String clock;
    public String parentId;
    public String targetId;
    public int    position;

    public Message() {}

    public static Message join(String sessionCode, String uid) {
        Message m = new Message();
        m.type        = JOIN;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        return m;
    }
    public static Message leave(String sessionCode, String uid) {
        Message m = new Message();
        m.type        = LEAVE;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        return m;
    }
    public static Message cursorUpdate(String sessionCode, String uid, int line) {
        Message m = new Message();
        m.type        = CURSOR_POSITION;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        m.cursorLine  = line;
        return m;
    }
    public static Message insert(String sessionCode, String uid,
                                 String clock, String content, String parentId) {
        Message m = new Message();
        m.type        = INSERT;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        m.clock       = clock;
        m.content     = content;
        m.parentId    = parentId;
        return m;
    }
    public static Message delete(String sessionCode, String uid,
                                 String clock, String targetId) {
        Message m = new Message();
        m.type        = DELETE;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        m.clock       = clock;
        m.targetId    = targetId;
        return m;
    }
    public static Message split(String sessionCode, String uid,
                                String clock, int position) {
        Message m = new Message();
        m.type        = SPLIT;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        m.clock       = clock;
        m.position    = position;
        return m;
    }
    public static Message undo(String sessionCode, String uid) {
        Message m = new Message();
        m.type        = UNDO;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        return m;
    }
    public static Message redo(String sessionCode, String uid) {
        Message m = new Message();
        m.type        = REDO;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        return m;
    }
}