package com.shanananana.adagent.skills;

final class ToolNames {

    private ToolNames() {
    }

    static String sanitize(String id) {
        if (id == null || id.isBlank()) {
            return "skill";
        }
        String s = id.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_");
        if (s.isEmpty()) {
            return "skill";
        }
        char c = s.charAt(0);
        if (!(c >= 'a' && c <= 'z') && c != '_') {
            s = "skill_" + s;
        }
        return s;
    }
}
