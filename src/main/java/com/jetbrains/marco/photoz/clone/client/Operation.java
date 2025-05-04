package com.jetbrains.marco.photoz.clone.client;

public class Operation {
    public final String type; // "insert" or "delete"
    public final String nodeId;

    public Operation(String type, String nodeId) {
        this.type = type;
        this.nodeId = nodeId;
    }
}