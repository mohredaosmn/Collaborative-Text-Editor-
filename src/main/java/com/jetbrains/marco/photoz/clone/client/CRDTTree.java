package com.jetbrains.marco.photoz.clone.client;

import java.util.*;

public class CRDTTree {
    private final String rootId;
    private final Map<String, CRDTNode> nodes = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Deque<Operation> undoStack = new ArrayDeque<>();
    private final Deque<Operation> redoStack = new ArrayDeque<>();

    private static class Operation {
        final String type;
        final String nodeId;

        Operation(String type, String nodeId) {
            this.type = type;
            this.nodeId = nodeId;
        }
    }

    public CRDTTree() {
        // Initialize the root node
        CRDTNode root = new CRDTNode("", "0", "0", null);
        this.rootId = root.id;
        nodes.put(root.id, root);
        children.put(root.id, new ArrayList<>());
    }

    /**
     * Returns the root node's ID.
     */
    public String getRootId() {
        return rootId;
    }

    public void insert(String value, String uid, String clock, String parentId) {
        if (!nodes.containsKey(parentId)) {
            throw new IllegalArgumentException("Invalid parentId: " + parentId);
        }

        CRDTNode node = new CRDTNode(value, uid, clock, parentId);
        nodes.put(node.id, node);
        children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node.id);
        sortChildren(parentId);
        undoStack.push(new Operation("insert", node.id));
        redoStack.clear();
    }

    public void delete(String id) {
        CRDTNode node = nodes.get(id);
        if (node != null && !node.deleted) {
            node.deleted = true;
            undoStack.push(new Operation("delete", id));
            redoStack.clear();
        }
    }

    public void splitAtPosition(int pos) {
        List<String> ordered = new ArrayList<>();
        flatten(rootId, ordered);
        int idx = 0;
        String splitNodeId = null;
        int splitPosInNode = 0;

        for (String id : ordered) {
            CRDTNode n = nodes.get(id);
            if (!n.deleted) {
                if (idx + n.value.length() > pos) {
                    splitNodeId = id;
                    splitPosInNode = pos - idx;
                    break;
                }
                idx += n.value.length();
            }
        }

        if (splitNodeId != null && splitPosInNode > 0) {
            CRDTNode originalNode = nodes.get(splitNodeId);
            String uid = originalNode.uid;
            long timestamp = System.nanoTime();

            String firstPart = originalNode.value.substring(0, splitPosInNode);
            String secondPart = originalNode.value.substring(splitPosInNode);

            originalNode.deleted = true;

            // Insert first part
            insert(firstPart, uid, String.valueOf(timestamp), originalNode.parentId);
            // Insert newline as separator
            insert("\n", uid, String.valueOf(timestamp + 1), uid + "-" + timestamp);
            // Insert second part
            insert(secondPart, uid, String.valueOf(timestamp + 2), uid + "-" + (timestamp + 1));

            // Reattach children
            if (children.containsKey(splitNodeId)) {
                List<String> childIds = new ArrayList<>(children.get(splitNodeId));
                for (String childId : childIds) {
                    CRDTNode child = nodes.get(childId);
                    insert(child.value, child.uid, String.valueOf(timestamp + 3), child.parentId.equals(splitNodeId)
                            ? uid + "-" + (timestamp + 2)
                            : child.parentId);
                    nodes.get(child.uid + "-" + (timestamp + 3)).deleted = child.deleted;
                    child.deleted = true;
                }
                children.remove(splitNodeId);
            }
        }
    }

    public String getDocument() {
        StringBuilder sb = new StringBuilder();
        buildText(rootId, sb);
        return sb.toString();
    }

    private void buildText(String parentId, StringBuilder sb) {
        List<String> kids = children.getOrDefault(parentId, Collections.emptyList());
        kids.sort(this::compareNodeIds);
        for (String id : kids) {
            CRDTNode node = nodes.get(id);
            if (!node.deleted) {
                sb.append(node.value);
            }
            buildText(id, sb);
        }
    }

    public String getCharIdAtPosition(int pos) {
        if (pos < 0) return null;
        List<String> ordered = new ArrayList<>();
        flatten(rootId, ordered);
        int idx = 0;
        for (String id : ordered) {
            CRDTNode n = nodes.get(id);
            if (!n.deleted) {
                if (idx == pos) {
                    return id;
                }
                idx += n.value.length();
            }
        }
        return null;
    }

    public String getParentIdForInsertAtPosition(int pos) {
        if (pos <= 0) return rootId;
        List<String> ordered = new ArrayList<>();
        flatten(rootId, ordered);
        int idx = 0;
        String lastId = rootId;
        for (String id : ordered) {
            CRDTNode n = nodes.get(id);
            if (!n.deleted) {
                if (idx >= pos) return lastId;
                lastId = id;
                idx += n.value.length();
            }
        }
        return lastId;
    }

    private void flatten(String parentId, List<String> out) {
        List<String> kids = children.getOrDefault(parentId, Collections.emptyList());
        kids.sort(this::compareNodeIds);
        for (String id : kids) {
            out.add(id);
            flatten(id, out);
        }
    }

    private int compareNodeIds(String id1, String id2) {
        CRDTNode n1 = nodes.get(id1), n2 = nodes.get(id2);
        long t1 = Long.parseLong(n1.clock), t2 = Long.parseLong(n2.clock);
        if (t1 != t2) return Long.compare(t1, t2);
        return n1.uid.compareTo(n2.uid);
    }

    private void sortChildren(String parentId) {
        List<String> kids = children.getOrDefault(parentId, new ArrayList<>());
        kids.sort(this::compareNodeIds);
        children.put(parentId, kids);
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            Operation op = undoStack.pop();
            CRDTNode node = nodes.get(op.nodeId);
            if (op.type.equals("insert")) {
                node.deleted = true;
            } else if (op.type.equals("delete")) {
                node.deleted = false;
            }
            redoStack.push(op);
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Operation op = redoStack.pop();
            CRDTNode node = nodes.get(op.nodeId);
            if (op.type.equals("insert")) {
                node.deleted = false;
            } else if (op.type.equals("delete")) {
                node.deleted = true;
            }
            undoStack.push(op);
        }
    }
}
