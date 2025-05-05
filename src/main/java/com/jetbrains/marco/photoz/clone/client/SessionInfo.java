package com.jetbrains.marco.photoz.clone.client;

/** Holds one session’s UUID and its two shareable codes. */
public class SessionInfo {
    public final String sessionId;
    public final String rwCode;
    public final String roCode;

    public SessionInfo(String sessionId, String rwCode, String roCode) {
        this.sessionId = sessionId;
        this.rwCode    = rwCode;
        this.roCode    = roCode;
    }
}