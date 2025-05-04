package com.jetbrains.marco.photoz.clone.common;

/**
 * Unified message format for CRDT operations, cursor tracking, and session announcements.
 */
public class Message {
    // Message types
    public static final String JOIN              = "join";
    public static final String LEAVE             = "leave";
    public static final String CURSOR_POSITION   = "cursor_position";
    public static final String SESSION           = "session";
    public static final String REQUEST_SESSIONS  = "request_sessions";
    public static final String INSERT            = "insert";
    public static final String DELETE            = "delete";
    public static final String UNDO              = "undo";
    public static final String REDO              = "redo";
    public static final String SPLIT             = "split";

    // Core fields
    public String type;
    public String sessionCode;
    public String uid;
    public long   timestamp = System.currentTimeMillis();

    // Cursor tracking
    public int    cursorLine;

    // CRDT operations
    public String content;
    public String clock;
    public String parentId;
    public String targetId;
    public int    position;

    // Empty constructor for JSON
    public Message() {}

    // --------- Factory Methods --------- //

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

    /** Announce a newly created session code. */
    public static Message createSession(String sessionCode, String uid) {
        Message m = new Message();
        m.type        = SESSION;
        m.sessionCode = sessionCode;
        m.uid         = uid;
        return m;
    }

    /** Request the current list of sessions from all peers. */
    public static Message requestSessions(String sessionCode, String uid) {
        Message m = new Message();
        m.type        = REQUEST_SESSIONS;
        m.sessionCode = sessionCode;
        m.uid         = uid;
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
}