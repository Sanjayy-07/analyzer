package com.archanalyzer.simulator;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.pipeline.ProjectDiscovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WhatIfSimulatorTest {

    private ProjectDiscovery discovery;
    private MetricsEngine metricsEngine;
    private ChangeProposalParser parser;
    private WhatIfSimulator simulator;
    private ChangeImplementationEngine implementationEngine;

    @BeforeEach
    void setUp() {
        discovery = new ProjectDiscovery();
        metricsEngine = new MetricsEngine();
        parser = new ChangeProposalParser();
        simulator = new WhatIfSimulator();
        implementationEngine = new ChangeImplementationEngine();
    }

    private Path createSampleShopProject(Path tempDir) throws IOException {
        Path src = tempDir.resolve("src/main/java/com/demo/shop/service");
        Path ctrl = tempDir.resolve("src/main/java/com/demo/shop/controller");
        Files.createDirectories(src);
        Files.createDirectories(ctrl);

        Files.writeString(src.resolve("PaymentService.java"),
            "package com.demo.shop.service;\n" +
            "public class PaymentService {\n" +
            "    public boolean charge(String custId, double amount) { return true; }\n" +
            "}\n");

        Files.writeString(src.resolve("OrderService.java"),
            "package com.demo.shop.service;\n" +
            "public class OrderService {\n" +
            "    private PaymentService paymentService = new PaymentService();\n" +
            "    public void checkout(String custId) { paymentService.charge(custId, 100.0); }\n" +
            "}\n");

        Files.writeString(ctrl.resolve("OrderController.java"),
            "package com.demo.shop.controller;\n" +
            "import com.demo.shop.service.OrderService;\n" +
            "public class OrderController {\n" +
            "    private OrderService orderService = new OrderService();\n" +
            "    public void postOrder() { orderService.checkout(\"c1\"); }\n" +
            "}\n");

        return tempDir;
    }

    @Test
    void testNaturalLanguageProposalParsing(@TempDir Path tempDir) throws IOException {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");

        // Scenario A: Add refund to payment module
        ChangeRequest reqA = parser.parse(
            "What happens if I add a refund functionality to the payment module?",
            null, null, model
        );
        assertEquals(ChangeOperationType.ADD_FEATURE, reqA.getOperation());
        assertNotNull(reqA.getTargetComponentId());
        assertTrue(reqA.getTargetComponentId().contains("PaymentService"), "Must resolve to PaymentService");
        assertFalse(reqA.getProposedOperations().isEmpty());
        assertFalse(reqA.getAssumptions().isEmpty());
        assertFalse(reqA.getValidationPlan().isEmpty());

        // Scenario B: Remove search from home page
        ChangeRequest reqB = parser.parse(
            "What happens if I remove search functionality from the home page?",
            null, null, model
        );
        assertEquals(ChangeOperationType.REMOVE_FEATURE, reqB.getOperation());

        // Scenario C: Modify API response structure
        ChangeRequest reqC = parser.parse(
            "What happens if I change the response structure of the user API?",
            null, null, model
        );
        assertEquals(ChangeOperationType.MODIFY_API, reqC.getOperation());

        // Scenario D: Add notification module
        ChangeRequest reqD = parser.parse(
            "What happens if I introduce a notification module to this project?",
            null, null, model
        );
        assertEquals(ChangeOperationType.ADD_MODULE, reqD.getOperation());

        // Scenario E: Modify database entity
        ChangeRequest reqE = parser.parse(
            "What happens if I remove the phoneNumber field from the user entity?",
            null, null, model
        );
        assertEquals(ChangeOperationType.MODIFY_DATABASE_ENTITY, reqE.getOperation());
    }

    @Test
    void testAmbiguityHandlingWhenMultipleComponentsMatch(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src/main/java/com/demo");
        Files.createDirectories(src);
        Files.writeString(src.resolve("PaymentGateway.java"), "package com.demo;\npublic class PaymentGateway {}\n");
        Files.writeString(src.resolve("PaymentService.java"), "package com.demo;\npublic class PaymentService {}\n");
        Files.writeString(src.resolve("PaymentController.java"), "package com.demo;\npublic class PaymentController {}\n");

        ProjectModel model = discovery.discoverAndParse(tempDir, "PaymentApp");

        ChangeRequest req = parser.parse("What happens if I change the payment component?", null, null, model);
        assertTrue(req.isAmbiguous(), "Must be flagged as ambiguous when multiple components match generic term");
        assertTrue(req.getCandidateMatches().size() >= 2);
        assertTrue(req.getConfidence() < 0.80);
        assertFalse(req.getUnresolvedQuestions().isEmpty());
    }

    @Test
    void testWhatIfSimulationTraversesDependentsAndComputesPaths(@TempDir Path tempDir) throws IOException {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        ChangeRequest req = parser.parse(
            "What happens if I add refund functionality to the payment module?",
            null, null, model
        );

        SimulationResult result = simulator.simulate(req, model, graph, metrics, null);

        assertNotNull(result);
        assertEquals("sim-", result.getSimulationId().substring(0, 4));

        // Direct dependent of PaymentService is OrderService
        assertTrue(result.getDirectlyAffectedComponents().stream().anyMatch(c -> c.contains("OrderService")),
            "OrderService must be directly affected");

        // Transitive/indirect dependent is OrderController
        assertTrue(result.getIndirectlyAffectedComponents().stream().anyMatch(c -> c.contains("OrderController")),
            "OrderController must be indirectly affected");

        // Dependency path must be recorded: OrderController -> OrderService -> PaymentService
        assertTrue(result.getDependencyPaths().containsKey("com.demo.shop.controller.OrderController"));
        List<String> path = result.getDependencyPaths().get("com.demo.shop.controller.OrderController");
        assertEquals(3, path.size());
        assertEquals("com.demo.shop.controller.OrderController", path.get(0));
        assertEquals("com.demo.shop.service.OrderService", path.get(1));
        assertEquals("com.demo.shop.service.PaymentService", path.get(2));
    }

    @Test
    void testRiskScoringIsDeterministicAndExplanatory(@TempDir Path tempDir) throws IOException {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        ChangeRequest req1 = parser.parse("What happens if I add refund functionality to the payment module?", null, null, model);
        ChangeRequest req2 = parser.parse("What happens if I add refund functionality to the payment module?", null, null, model);

        SimulationResult result1 = simulator.simulate(req1, model, graph, metrics, null);
        SimulationResult result2 = simulator.simulate(req2, model, graph, metrics, null);

        // Determinism: identical inputs must yield identical risk score
        assertEquals(result1.getRiskAssessment().overallScore, result2.getRiskAssessment().overallScore, 0.001);
        assertEquals(result1.getRiskAssessment().riskCategory, result2.getRiskAssessment().riskCategory);

        // Score must be in 0 - 100 range
        assertTrue(result1.getRiskAssessment().overallScore >= 0 && result1.getRiskAssessment().overallScore <= 100);

        // Contributing factors must not be empty
        assertFalse(result1.getRiskAssessment().contributingFactors.isEmpty());
        assertTrue(result1.getRiskAssessment().contributingFactors.stream().anyMatch(f -> f.contains("downstream")));
    }

    @Test
    void testSimulationNeverModifiesOriginalSourceProject(@TempDir Path tempDir) throws Exception {
        Path projRoot = createSampleShopProject(tempDir);
        Path paymentFile = projRoot.resolve("src/main/java/com/demo/shop/service/PaymentService.java");
        long initialModified = Files.getLastModifiedTime(paymentFile).toMillis();
        String initialContent = Files.readString(paymentFile);

        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        ChangeRequest req = parser.parse("What happens if I add refund functionality to the payment module?", null, null, model);
        SimulationResult result = simulator.simulate(req, model, graph, metrics, null);

        assertNotNull(result);
        assertFalse(result.isApproved());
        assertFalse(result.isApplied());

        // File must remain 100% untouched
        assertEquals(initialContent, Files.readString(paymentFile), "Source file must not be modified during simulation");
        assertEquals(initialModified, Files.getLastModifiedTime(paymentFile).toMillis(), "File timestamp must not change");
    }

    @Test
    void testApprovalRequiredBeforeImplementation(@TempDir Path tempDir) throws IOException {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        ChangeRequest req = parser.parse("What happens if I add refund functionality to the payment module?", null, null, model);
        SimulationResult simResult = simulator.simulate(req, model, graph, metrics, null);

        assertFalse(simResult.isApproved());

        // Attempting implementation without approval MUST throw IllegalStateException
        assertThrows(IllegalStateException.class, () ->
            implementationEngine.executeApprovedChange(simResult, projRoot, tempDir.resolve("work")));
    }

    @Test
    void testApprovedChangeAppliesCompilesAndPackagesDeliverable(@TempDir Path tempDir) throws Exception {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        ChangeRequest req = parser.parse("What happens if I add refund functionality to the payment module?", null, null, model);
        SimulationResult simResult = simulator.simulate(req, model, graph, metrics, null);

        // User approves change
        simResult.setApproved(true);
        Path workDir = tempDir.resolve("work");

        var implResult = implementationEngine.executeApprovedChange(simResult, projRoot, workDir);

        assertTrue(implResult.success);
        assertFalse(implResult.rolledBack);
        assertEquals("ACCEPTED_AND_VALIDATED", implResult.status);
        assertNotNull(implResult.finalZipPath);
        assertTrue(Files.exists(implResult.finalZipPath));
        assertTrue(implResult.finalZipPath.getFileName().toString().endsWith("-modified.zip"),
            "Accepted change deliverable must end with -modified.zip");

        // Original project remains pristine
        String originalPayment = Files.readString(projRoot.resolve("src/main/java/com/demo/shop/service/PaymentService.java"));
        assertFalse(originalPayment.contains("void refund("));
    }

    @Test
    void testIntroduceInterfaceParsesAndSimulatesGraphCopy(@TempDir Path tempDir) throws Exception {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        int edgesBefore = graph.edgeCount();
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        ChangeRequest req = parser.parse(
            "I want to introduce an interface between OrderService and PaymentService to reduce direct coupling.",
            null, null, model
        );
        assertEquals(ChangeOperationType.INTRODUCE_INTERFACE, req.getOperation());
        assertTrue(req.getTargetComponentId().contains("OrderService"));
        assertTrue(req.getSecondaryComponentId().contains("PaymentService"));
        assertNotNull(req.getNewComponentId());

        SimulationResult result = simulator.simulate(req, model, graph, metrics, null);
        assertEquals(edgesBefore, graph.edgeCount(), "Baseline graph must not be mutated");
        assertFalse(result.isOriginalModelMutated());
        assertNotNull(result.getChangePlan());
        assertTrue(result.getChangePlan().autoApplicable);
        assertTrue(result.getProjectedMetrics().totalComponents > metrics.totalComponents);
        assertTrue(result.getProposedGraph().containsVertex(req.getNewComponentId()));
        assertTrue(result.getProposedGraph().outgoing("com.demo.shop.service.OrderService")
            .contains(req.getNewComponentId()));
        assertFalse(result.getProposedGraph().outgoing("com.demo.shop.service.OrderService")
            .contains("com.demo.shop.service.PaymentService"));
        assertTrue(result.getBaselineGraphPayload().containsKey("nodes"));
        assertTrue(result.getProposedGraphPayload().containsKey("edges"));
        assertFalse(result.getChangePlan().alternatives.isEmpty());
    }

    @Test
    void testAmbiguousRequestDoesNotSilentlyGuessSingleTargetWhenManyMatch(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src/main/java/com/demo");
        Files.createDirectories(src);
        Files.writeString(src.resolve("AlphaService.java"), "package com.demo;\npublic class AlphaService {}\n");
        ProjectModel model = discovery.discoverAndParse(tempDir, "Tiny");
        ChangeRequest req = parser.parse("   ", null, null, model);
        assertEquals(ChangeOperationType.OTHER, req.getOperation());
        assertTrue(req.getConfidence() < 0.1);
    }

    @Test
    void testUnsupportedRemoveDependencyIsNotAutoApplicable(@TempDir Path tempDir) throws Exception {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);
        ChangeRequest req = parser.parse(
            "Remove the dependency between OrderService and PaymentService",
            "com.demo.shop.service.OrderService",
            ChangeOperationType.REMOVE_DEPENDENCY,
            model
        );
        req.setSecondaryComponentId("com.demo.shop.service.PaymentService");
        SimulationResult result = simulator.simulate(req, model, graph, metrics, null);
        assertFalse(result.getChangePlan().autoApplicable);
        assertNotNull(result.getChangePlan().unsupportedReason);
        assertFalse(result.getProposedGraph().outgoing("com.demo.shop.service.OrderService")
            .contains("com.demo.shop.service.PaymentService"));
    }

    @Test
    void testApprovedInterfaceIntroductionWritesInterfaceAndPreservesOriginal(@TempDir Path tempDir) throws Exception {
        Path projRoot = createSampleShopProject(tempDir);
        ProjectModel model = discovery.discoverAndParse(projRoot, "ShopApp");
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);
        ChangeRequest req = parser.parse(
            "I want to introduce an interface between OrderService and PaymentService to reduce direct coupling.",
            null, null, model
        );
        SimulationResult sim = simulator.simulate(req, model, graph, metrics, null);
        sim.setApproved(true);
        var impl = implementationEngine.executeApprovedChange(sim, projRoot, tempDir.resolve("work"));
        assertTrue(impl.success, impl.summary);
        Path iface = tempDir.resolve("work/refactored/src/main/java/com/demo/shop/service/IPaymentService.java");
        assertTrue(Files.exists(iface), "Generated interface must be present in isolated copy");
        String client = Files.readString(tempDir.resolve("work/refactored/src/main/java/com/demo/shop/service/OrderService.java"));
        assertTrue(client.contains("IPaymentService"));
        String originalClient = Files.readString(projRoot.resolve("src/main/java/com/demo/shop/service/OrderService.java"));
        assertFalse(originalClient.contains("IPaymentService"));
    }
}
