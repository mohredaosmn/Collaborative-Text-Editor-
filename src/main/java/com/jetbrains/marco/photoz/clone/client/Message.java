package com.jetbrains.marco.photoz.clone.client;

public class Message {
    public String type;
    public String uid;
    public String sessionCode;
    public String clock;
    public String value;
    public String parentId;
    public String targetId;
    public int position;

    // Default constructor required for JSON deserialization
    public Message() {}

    // Constructor for control messages (undo/redo/join)
    public Message(String type, String uid, String sessionCode) {
        this.type = type;
        this.uid = uid;
        this.sessionCode = sessionCode;
    }

    // Constructor for insert operations
    public Message(String type, String uid, String sessionCode, String clock, String value, String parentId) {
        this.type = type;
        this.uid = uid;
        this.sessionCode = sessionCode;
        this.clock = clock;
        this.value = value;
        this.parentId = parentId;
    }

    // Constructor for delete operations
    public Message(String type, String uid, String sessionCode, String clock, String targetId) {
        this.type = type;
        this.uid = uid;
        this.sessionCode = sessionCode;
        this.clock = clock;
        this.targetId = targetId;
    }

    // Constructor for split operations
    public Message(String type, String uid, String sessionCode, String clock, int position) {
        this.type = type;
        this.uid = uid;
        this.sessionCode = sessionCode;
        this.clock = clock;
        this.position = position;
    }
}