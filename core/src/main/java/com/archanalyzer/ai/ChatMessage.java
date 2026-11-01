package com.archanalyzer.ai;

import java.io.Serializable;

/**
 * Represents a single message turn in a multi-turn conversation.
 */
public record ChatMessage(String role, String content) implements Serializable {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    public ChatMessage {
        if (role == null || role.isBlank()) {
            role = ROLE_USER;
        }
        if (content == null) {
            content = "";
        }
    }
}
