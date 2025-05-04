package com.jetbrains.marco.photoz.clone.common;

public class Message {
    public String type; // "join", "insert", "delete", "cursor"
    public String sessionCode;
    public String uid;
    public String clock;
    public String value; // for inserts & cursors (e.g. "\n" or caret position)
    public String parentId; // for CRDT ordering
    public String targetId; // for deletions
}
