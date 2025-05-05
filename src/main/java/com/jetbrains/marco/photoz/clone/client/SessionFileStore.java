package com.jetbrains.marco.photoz.clone.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SessionFileStore {
    private static final Path PATH = Paths.get(System.getProperty("user.home"), ".collab_sessions.json");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String,SessionInfo> sessions      = new LinkedHashMap<>();
    private final Map<String,String>       codeToSession = new LinkedHashMap<>();

    public SessionFileStore() {
        reload();
    }

    /** Re‐read the JSON file and repopulate in‐memory maps. */
    public void reload() {
        sessions.clear();
        codeToSession.clear();
        if (!Files.exists(PATH)) return;

        try (InputStream in = Files.newInputStream(PATH)) {
            List<SessionInfo> list = mapper.readValue(in, new TypeReference<>() {});
            for (SessionInfo si : list) {
                sessions.put(si.sessionId, si);
                codeToSession.put(si.rwCode, si.sessionId);
                codeToSession.put(si.roCode, si.sessionId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveAll() {
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                mapper.writerWithDefaultPrettyPrinter()
                      .writeValue(out, sessions.values());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Persist a new or updated session. */
    public void save(SessionInfo si) {
        sessions.put(si.sessionId, si);
        codeToSession.put(si.rwCode, si.sessionId);
        codeToSession.put(si.roCode, si.sessionId);
        saveAll();
    }

    /** Always returns the freshest info for a code. */
    public SessionInfo findByCode(String code) {
        reload();
        String sid = codeToSession.get(code);
        return sid == null ? null : sessions.get(sid);
    }
}