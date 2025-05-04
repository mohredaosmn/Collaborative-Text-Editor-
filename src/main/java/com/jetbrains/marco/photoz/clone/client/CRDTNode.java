package com.jetbrains.marco.photoz.clone.client;

public class CRDTNode {
    public final String value;
    public final String id;
    public final String uid;
    public final String clock;
    public final String parentId;
    public boolean deleted;

    public CRDTNode(String value, String uid, String clock, String parentId) {
        this.value = value;
        this.id = uid + "-" + clock;
        this.uid = uid;
        this.clock = clock;
        this.parentId = parentId;
        this.deleted = false;
    }
}