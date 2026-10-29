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
}
