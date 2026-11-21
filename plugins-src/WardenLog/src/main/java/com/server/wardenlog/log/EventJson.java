package com.server.wardenlog.log;

import java.time.Instant;

/**
 * Lightweight zero-dependency JSON line builder with RFC 8259 string escaping.
 */
public final class EventJson {

    private final StringBuilder sb = new StringBuilder(160);

    private EventJson(String eventName) {
        sb.append("{\"timestamp\":\"").append(escape(Instant.now().toString()))
                .append("\", \"event\":\"").append(escape(eventName)).append('"');
    }

    public static EventJson of(String eventName) {
        return new EventJson(eventName);
    }

    public EventJson field(String key, String value) {
        sb.append(", \"").append(escape(key)).append("\":\"").append(escape(value)).append('"');
        return this;
    }

    public EventJson field(String key, int value) {
        sb.append(", \"").append(escape(key)).append("\":").append(value);
        return this;
    }

    public EventJson xyz(int x, int y, int z, String world) {
        return field("x", x).field("y", y).field("z", z).field("world", world);
    }

    public EventJson xz(int x, int z, String world) {
        return field("x", x).field("z", z).field("world", world);
    }

    public String build() {
        return sb.append('}').toString();
    }

    /** Escapes JSON special and control characters; preserves literal "null" for null input. */
    public static String escape(String str) {
        if (str == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }
}

