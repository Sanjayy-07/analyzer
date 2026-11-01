package com.archanalyzer.ai;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.smell.SmellDetector;
import com.archanalyzer.smell.SmellDetector.Smell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiExplanationServiceTest {

    private ProjectModel model;
    private ProjectMetrics metrics;
    private List<Smell> smells;

    @BeforeEach
    void setUp() {
        model = new ProjectModel("test-project");
        model.addComponent(new ProjectModel.ComponentNode(
            "com.example.OrderService", "OrderService", "com.example",
            ProjectModel.ComponentNode.Kind.CLASS, "com/example/OrderService.java"
        ));
        model.addComponent(new ProjectModel.ComponentNode(
            "com.example.PaymentService", "PaymentService", "com.example",
            ProjectModel.ComponentNode.Kind.CLASS, "com/example/PaymentService.java"
        ));

        metrics = new ProjectMetrics();
        smells = new ArrayList<>();
        smells.add(new Smell(
            "com.example.OrderService", "CIRCULAR_DEPENDENCY",
            SmellDetector.Severity.HIGH, "Cycles between OrderService and PaymentService"
        ));
    }

    @Test
    void testUnconfiguredServiceReturnsUnconfiguredStatus() {
        AiConfig config = AiConfig.disabled();
        AiExplanationService service = new AiExplanationService(config);

        AiAnalysisResult result = service.analyzeArchitecture(model, metrics, smells, List.of());

        assertNotNull(result);
        assertEquals(AiAnalysisResult.Status.UNCONFIGURED, result.getStatus());
        assertFalse(result.isAvailable());
        assertTrue(result.getExplanations().isEmpty());
        assertTrue(result.getRecommendations().isEmpty());
        assertTrue(result.getMessage().contains("unconfigured"));
    }

    @Test
    void testValidStructuredJsonParsesCorrectly() {
        AiConfig config = new AiConfig("mock", "test-key", "https://api.test", "test-model", 30);
        MockLlmProvider mockProvider = new MockLlmProvider("TestProvider", "test-model");

        String validJson = """
            {
              "explanations": [
                {
                  "smellType": "CIRCULAR_DEPENDENCY",
                  "componentId": "com.example.OrderService",
                  "evidence": "OrderService depends on PaymentService",
                  "plainLanguageExplanation": "Both classes depend directly on each other, forming a tight loop.",
                  "whyItMatters": "Changes to OrderService require testing and compiling PaymentService and vice versa.",
                  "maintainabilityImpact": "High coupling, difficult unit testing.",
                  "recommendedAction": "Extract an interface for PaymentService.",
                  "priority": "HIGH",
                  "confidence": "High"
                }
              ],
              "recommendations": [
                {
                  "id": "rec-1",
                  "targetComponentId": "com.example.OrderService",
                  "detectedProblem": "Bidirectional coupling with PaymentService",
                  "proposedRefactoring": "Extract Interface",
                  "whyItHelps": "Breaks direct dependency cycle using Dependency Inversion.",
                  "expectedBenefits": "Decoupled testing and independent deployability.",
                  "potentialRisks": "Adds an extra interface file to maintain.",
                  "preconditions": "PaymentService methods are public.",
                  "engineSupported": true,
                  "implementationPlan": "1. Extract IPaymentService. 2. Implement in PaymentService. 3. Update OrderService reference.",
                  "validationRequirements": "All existing unit tests pass and compilation succeeds."
                }
              ]
            }
            """;
        mockProvider.setCustomResponder(prompt -> validJson);

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        AiAnalysisResult result = service.analyzeArchitecture(model, metrics, smells, List.of());

        assertNotNull(result);
        assertEquals(AiAnalysisResult.Status.LIVE_SUCCESS, result.getStatus());
        assertTrue(result.isAvailable());
        assertEquals("TestProvider", result.getProvider());
        assertEquals("test-model", result.getModel());

        assertEquals(1, result.getExplanations().size());
        ArchitectureSmellExplanation exp = result.getExplanations().get(0);
        assertEquals("CIRCULAR_DEPENDENCY", exp.getSmellType());
        assertEquals("com.example.OrderService", exp.getComponentId());
        assertEquals("HIGH", exp.getPriority());
        assertEquals("High", exp.getConfidence());
        assertTrue(exp.getPlainLanguageExplanation().contains("tight loop"));

        assertEquals(1, result.getRecommendations().size());
        AiRefactoringRecommendation rec = result.getRecommendations().get(0);
        assertEquals("rec-1", rec.getId());
        assertEquals("com.example.OrderService", rec.getTargetComponentId());
        assertTrue(rec.isEngineSupported());
        assertTrue(rec.getProposedRefactoring().contains("Extract Interface"));
    }

    @Test
    void testHallucinationDefenseFiltersUnknownComponents() {
        AiConfig config = new AiConfig("mock", "test-key", "https://api.test", "test-model", 30);
        MockLlmProvider mockProvider = new MockLlmProvider();

        // Response contains one valid component (com.example.OrderService) and one hallucinated (com.ghost.NonExistentClass)
        String jsonWithHallucination = """
            {
              "explanations": [
                {
                  "smellType": "CIRCULAR_DEPENDENCY",
                  "componentId": "com.example.OrderService",
                  "plainLanguageExplanation": "Valid explanation."
                },
                {
                  "smellType": "GOD_CLASS",
                  "componentId": "com.ghost.NonExistentClass",
                  "plainLanguageExplanation": "Fabricated class explanation."
                }
              ],
              "recommendations": [
                {
                  "id": "rec-valid",
                  "targetComponentId": "com.example.PaymentService",
                  "proposedRefactoring": "Extract Interface",
                  "engineSupported": true
                },
                {
                  "id": "rec-ghost",
                  "targetComponentId": "com.ghost.FakeService",
                  "proposedRefactoring": "Rewrite in Rust",
                  "engineSupported": false
                }
              ]
            }
            """;
        mockProvider.setCustomResponder(prompt -> jsonWithHallucination);

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        AiAnalysisResult result = service.analyzeArchitecture(model, metrics, smells, List.of());

        assertEquals(AiAnalysisResult.Status.LIVE_SUCCESS, result.getStatus());
        // Only the valid component should remain in explanations
        assertEquals(1, result.getExplanations().size());
        assertEquals("com.example.OrderService", result.getExplanations().get(0).getComponentId());

        // Only the valid component should remain in recommendations
        assertEquals(1, result.getRecommendations().size());
        assertEquals("com.example.PaymentService", result.getRecommendations().get(0).getTargetComponentId());
    }

    @Test
    void testProviderFailureGracefulDegradation() {
        AiConfig config = new AiConfig("mock", "test-key", "https://api.test", "test-model", 30);
        MockLlmProvider mockProvider = new MockLlmProvider("OpenAI", "gpt-4o");
        mockProvider.setSimulateFailure(true, "ConnectException: Connection refused to api.openai.com");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        AiAnalysisResult result = service.analyzeArchitecture(model, metrics, smells, List.of());

        assertNotNull(result);
        assertEquals(AiAnalysisResult.Status.FAILED, result.getStatus());
        assertFalse(result.isAvailable());
        assertTrue(result.getErrorMessage().contains("Connection refused"));
        assertTrue(result.getExplanations().isEmpty());
        assertTrue(result.getRecommendations().isEmpty());
    }

    @Test
    void testInvalidJsonResponseFailsGracefully() {
        AiConfig config = new AiConfig("mock", "test-key", "https://api.test", "test-model", 30);
        MockLlmProvider mockProvider = new MockLlmProvider();
        mockProvider.setCustomResponder(prompt -> "I cannot analyze this as an AI text model.");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        AiAnalysisResult result = service.analyzeArchitecture(model, metrics, smells, List.of());

        assertNotNull(result);
        assertEquals(AiAnalysisResult.Status.FAILED, result.getStatus());
        assertFalse(result.isAvailable());
        assertTrue(result.getErrorMessage().contains("Failed to parse AI response"));
    }

    @Test
    void testAskArchitectLiveGeminiSuccess() {
        AiConfig config = new AiConfig("gemini", "AIzaSyFakeKeyForTest123", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setCustomResponder(prompt -> "To decouple PaymentService from OrderService, introduce IPaymentService interface.");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "How should I decouple PaymentService?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("GEMINI", response.getSource());
        assertEquals("CONNECTED", response.getStatus());
        assertNull(response.getNotice());
        assertTrue(response.isLiveAi());
        assertTrue(response.getReply().contains("IPaymentService interface"));
    }

    @Test
    void testAskArchitectUnconfiguredFallback() {
        AiConfig config = AiConfig.disabled();
        AiExplanationService service = new AiExplanationService(config);
        metrics.cycleCount = 1;

        ArchitectChatResponse response = service.askArchitect(
            "How can I fix the circular dependency?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("OFFLINE", response.getSource());
        assertEquals("UNCONFIGURED", response.getStatus());
        assertFalse(response.isLiveAi());
        assertNotNull(response.getNotice());
        assertTrue(response.getNotice().contains("offline mode"));
        assertTrue(response.getReply().contains("Extract Interface"));
    }

    @Test
    void testAskArchitectAuthenticationFailureFallback() {
        AiConfig config = new AiConfig("gemini", "AIzaSyInvalidKey123", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setSimulateFailure(true, "Gemini authentication failed (HTTP 401): Invalid or unauthorized API key.");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "How can I safely modify the services?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("OFFLINE", response.getSource());
        assertEquals("OFFLINE_FALLBACK", response.getStatus());
        assertFalse(response.isLiveAi());
        assertNotNull(response.getNotice());
        assertTrue(response.getNotice().contains("authentication failed"));
        assertTrue(response.getReply().contains("Presentation Layer"));
    }

    @Test
    void testAskArchitectQuotaRateLimitFailureFallback() {
        AiConfig config = new AiConfig("gemini", "AIzaSyRateLimitedKey", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setSimulateFailure(true, "Gemini quota or rate limit exceeded (HTTP 429): Quota exceeded for quota metric");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "What refactoring changes will improve the architecture score most?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("OFFLINE", response.getSource());
        assertEquals("OFFLINE_FALLBACK", response.getStatus());
        assertFalse(response.isLiveAi());
        assertNotNull(response.getNotice());
        assertTrue(response.getNotice().contains("rate limit") || response.getNotice().contains("quota"));
    }

    @Test
    void testAskArchitectTimeoutFailureFallback() {
        AiConfig config = new AiConfig("gemini", "AIzaSyValidKey", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setSimulateFailure(true, "Gemini model gemini-3.5-flash request timed out (request timed out)");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "How to improve architecture?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("OFFLINE", response.getSource());
        assertEquals("OFFLINE_FALLBACK", response.getStatus());
        assertNotNull(response.getNotice());
        assertTrue(response.getNotice().contains("timed out"));
    }

    @Test
    void testAskArchitectServiceUnavailable503Fallback() {
        AiConfig config = new AiConfig("gemini", "AIzaSyValidKey", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setSimulateFailure(true, "Gemini model gemini-3.5-flash unavailable (HTTP 503): High demand spike");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "How to improve architecture?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("OFFLINE", response.getSource());
        assertEquals("OFFLINE_FALLBACK", response.getStatus());
        assertNotNull(response.getNotice());
        assertTrue(response.getNotice().contains("503") || response.getNotice().contains("high demand"));
    }

    @Test
    void testAskArchitectEmptyResponseFallback() {
        AiConfig config = new AiConfig("gemini", "AIzaSyValidKey", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setSimulateFailure(true, "Gemini returned empty text content");

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "How to improve architecture?", List.of(), model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("OFFLINE", response.getSource());
        assertEquals("OFFLINE_FALLBACK", response.getStatus());
        assertNotNull(response.getNotice());
        assertTrue(response.getNotice().contains("empty response"));
    }

    @Test
    void testRetiredModelNormalizationInConfig() {
        AiConfig config1 = new AiConfig("gemini", "AIzaSyTest", "", "gemini-1.5-flash", 90);
        assertEquals("gemini-3.5-flash", config1.getModel());

        AiConfig config2 = new AiConfig("gemini", "AIzaSyTest", "", "gemini-2.0-flash", 90);
        assertEquals("gemini-3.5-flash", config2.getModel());

        AiConfig config3 = new AiConfig("gemini", "AIzaSyTest", "", "gemini-2.5-flash", 90);
        assertEquals("gemini-3.5-flash", config3.getModel());
    }

    @Test
    void testAskArchitectPreservesConversationHistory() {
        AiConfig config = new AiConfig("gemini", "AIzaSyValidKey", "", "gemini-3.5-flash", 90);
        MockLlmProvider mockProvider = new MockLlmProvider("Google Gemini", "gemini-3.5-flash");
        mockProvider.setCustomResponder(prompt -> "Here is the implementation of IPaymentService.");

        List<ChatMessage> history = List.of(
            new ChatMessage("user", "How do I decouple PaymentService?"),
            new ChatMessage("assistant", "You can extract an interface named IPaymentService.")
        );

        AiExplanationService service = new AiExplanationService(config, mockProvider);
        ArchitectChatResponse response = service.askArchitect(
            "Can you write the Java code for that interface?", history, model, metrics, smells, List.of()
        );

        assertNotNull(response);
        assertEquals("GEMINI", response.getSource());
        assertEquals(2, mockProvider.getLastConversationHistory().size());
        assertEquals("user", mockProvider.getLastConversationHistory().get(0).role());
        assertEquals("How do I decouple PaymentService?", mockProvider.getLastConversationHistory().get(0).content());
        assertEquals("assistant", mockProvider.getLastConversationHistory().get(1).role());
        assertEquals("You can extract an interface named IPaymentService.", mockProvider.getLastConversationHistory().get(1).content());
        assertEquals("Can you write the Java code for that interface?", mockProvider.getLastUserPrompt());
    }
}
