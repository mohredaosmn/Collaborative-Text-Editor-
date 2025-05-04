package com.jetbrains.marco.photoz.done.client;

public class Message {
    private String type;          // Message type (e.g., "edit", "cursor_position")
    private String content;      // Message content
    private String senderUid;    // User ID of the sender
    private int cursorLine;      // Current line number of the user's cursor
    private long timestamp;      // Timestamp for ordering operations

    // Updated constructor to include cursorLine
    public Message(String type, String content, String senderUid, int cursorLine) {
        this.type = type;
        this.content = content;
        this.senderUid = senderUid;
        this.cursorLine = cursorLine;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public int getCursorLine() {
        return cursorLine;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setCursorLine(int cursorLine) {
        this.cursorLine = cursorLine;
    }

    // Optional: Override toString() for debugging
    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", content='" + content + '\'' +
                ", senderUid='" + senderUid + '\'' +
                ", cursorLine=" + cursorLine +
                ", timestamp=" + timestamp +
                '}';
    }
}