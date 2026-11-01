package com.archanalyzer.ai;

import java.io.Serializable;

/**
 * Result of an interactive architecture question containing the reply text,
 * the verified response source ("GEMINI" vs "OFFLINE"), connection status,
 * and any diagnostic notice if fallback occurred.
 */
public class ArchitectChatResponse implements Serializable {

    private final String reply;
    private final String source;    // "GEMINI", "OPENAI", "ANTHROPIC", "OFFLINE"
    private final String status;    // "CONNECTED", "OFFLINE_FALLBACK", "UNCONFIGURED"
    private final String notice;    // Optional diagnostic message explaining fallback or error

    public ArchitectChatResponse(String reply, String source, String status, String notice) {
        this.reply = reply != null ? reply : "";
        this.source = source != null ? source : "OFFLINE";
        this.status = status != null ? status : "UNKNOWN";
        this.notice = notice;
    }

    public String getReply() {
        return reply;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public String getNotice() {
        return notice;
    }

    public boolean isLiveAi() {
        return "GEMINI".equalsIgnoreCase(source) || "OPENAI".equalsIgnoreCase(source) || "ANTHROPIC".equalsIgnoreCase(source);
    }
}
