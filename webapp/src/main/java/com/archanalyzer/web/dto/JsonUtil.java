package com.archanalyzer.web.dto;

import java.util.Collection;
import java.util.Map;

/**
 * Minimal, dependency-free JSON serialization for the raw Servlet REST
 * endpoints (PART 29). Deliberately avoids requiring Jackson/Gson on the
 * classpath for these specific endpoints so they have no extra runtime
 * dependency beyond the JDK; Spring MVC endpoints elsewhere may still use
 * Jackson conventionally if added to the pom for richer DTO mapping.
 * Supports the subset of JSON needed here: objects, arrays, strings,
 * numbers, booleans, null.
 */
public final class JsonUtil {

    private JsonUtil() {}

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(entry.getKey()))).append("\":");
                write(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Collection<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                first = false;
                write(item, sb);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
