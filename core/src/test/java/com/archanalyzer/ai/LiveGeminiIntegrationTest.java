package com.archanalyzer.ai;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LiveGeminiIntegrationTest {

    @Test
    void testLiveGeminiTurn1AndTurn2() {
        AiConfig config = AiConfig.loadFromEnvironment();
        if (!config.isConfigured()) {
            System.out.println("[LiveGeminiIntegrationTest] Skipped: No API key configured in .env");
            return;
        }

        System.out.println("[LiveGeminiIntegrationTest] Using configured provider: " + config.getProvider() + ", model: " + config.getModel());
        AiExplanationService service = new AiExplanationService(config);

        ProjectModel model = new ProjectModel("E-Commerce-Core");
        model.addComponent(new ProjectModel.ComponentNode(
            "com.ecommerce.OrderController", "OrderController", "com.ecommerce",
            ProjectModel.ComponentNode.Kind.CLASS, "com/ecommerce/OrderController.java"
        ));
        model.addComponent(new ProjectModel.ComponentNode(
            "com.ecommerce.OrderService", "OrderService", "com.ecommerce",
            ProjectModel.ComponentNode.Kind.CLASS, "com/ecommerce/OrderService.java"
        ));

        ProjectMetrics metrics = new ProjectMetrics();
        metrics.architectureHealthScore = 88.5;
        metrics.dependencyScore = 14.2;
        metrics.cycleCount = 0;

        // Turn 1: Ask a specific question
        String turn1Question = "In one sentence, explain what OrderController does.";
        ArchitectChatResponse turn1Resp = service.askArchitect(turn1Question, List.of(), model, metrics, List.of(), List.of());

        System.out.println("=== TURN 1 RESPONSE ===");
        System.out.println("Source: " + turn1Resp.getSource());
        System.out.println("Status: " + turn1Resp.getStatus());
        System.out.println("Notice: " + turn1Resp.getNotice());
        System.out.println("Reply: " + turn1Resp.getReply());

        if ("OFFLINE".equals(turn1Resp.getSource()) && turn1Resp.getNotice() != null
                && turn1Resp.getNotice().contains("Network connection to Gemini API failed")) {
            System.out.println("[LiveGeminiIntegrationTest] Network is offline / sandbox isolated. Live test gracefully skipped.");
            org.junit.jupiter.api.Assumptions.abort("Network connection to Gemini API failed due to sandbox/offline isolation.");
            return;
        }

        assertEquals("GEMINI", turn1Resp.getSource(), "Turn 1 source must be GEMINI");
        assertEquals("CONNECTED", turn1Resp.getStatus(), "Turn 1 status must be CONNECTED");
        assertTrue(turn1Resp.isLiveAi(), "Turn 1 must be Live AI");
        assertNotNull(turn1Resp.getReply());
        assertFalse(turn1Resp.getReply().isBlank());

        // Turn 2: Follow-up question relying on Turn 1 context
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("user", turn1Question));
        history.add(new ChatMessage("assistant", turn1Resp.getReply()));

        String turn2Question = "Now, in one sentence, what should it delegate to?";
        ArchitectChatResponse turn2Resp = service.askArchitect(turn2Question, history, model, metrics, List.of(), List.of());

        System.out.println("=== TURN 2 RESPONSE (WITH HISTORY) ===");
        System.out.println("Source: " + turn2Resp.getSource());
        System.out.println("Status: " + turn2Resp.getStatus());
        System.out.println("Notice: " + turn2Resp.getNotice());
        System.out.println("Reply: " + turn2Resp.getReply());

        assertEquals("GEMINI", turn2Resp.getSource(), "Turn 2 source must be GEMINI");
        assertEquals("CONNECTED", turn2Resp.getStatus(), "Turn 2 status must be CONNECTED");
        assertTrue(turn2Resp.isLiveAi(), "Turn 2 must be Live AI");
        assertNotNull(turn2Resp.getReply());
        assertFalse(turn2Resp.getReply().isBlank());
    }
}
