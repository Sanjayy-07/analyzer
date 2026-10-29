package com.archanalyzer.pipeline;

import com.archanalyzer.archive.ZipExtractor;
import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.ml.RiskPrediction;
import com.archanalyzer.ml.RiskPredictionService;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.optimization.ConstraintSet;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.smell.SmellDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowVerificationTest {

    private AnalyzerPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new AnalyzerPipeline(true);
    }

    @Test
    void testPhase4ThreeFileProjectWorkflow(@TempDir Path tempDir) throws Exception {
        // 1. Prepare User.java, UserService.java, Main.java as specified
        String userCode = """
            public class User {
                public String getName() {
                    return "Sanjay";
                }
            }
            """;

        String userServiceCode = """
            public class UserService {
                private final User user = new User();

                public String getUserName() {
                    return user.getName();
                }
            }
            """;

        String mainCode = """
            public class Main {
                public static void main(String[] args) {
                    UserService service = new UserService();
                    System.out.println(service.getUserName());
                }
            }
            """;

        // Create a ZIP archive
        Path zipFile = tempDir.resolve("phase4-test-project.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            addZipEntry(zos, "src/User.java", userCode);
            addZipEntry(zos, "src/UserService.java", userServiceCode);
            addZipEntry(zos, "src/Main.java", mainCode);
        }

        assertTrue(Files.exists(zipFile));

        // 2. Safe Extraction
        Path extractDir = tempDir.resolve("extracted");
        ZipExtractor extractor = new ZipExtractor();
        extractor.extract(zipFile, extractDir);

        // 3. Discovery and Parsing
        ProjectDiscovery discovery = new ProjectDiscovery(true);
        ProjectModel model = discovery.discoverAndParse(extractDir, "Phase4Demo");

        assertEquals(3, model.fileCount(), "Must discover exactly 3 Java source files");
        assertEquals(3, model.componentCount(), "Must recognize exactly 3 classes: User, UserService, Main");
        assertTrue(model.getComponent("User").isPresent());
        assertTrue(model.getComponent("UserService").isPresent());
        assertTrue(model.getComponent("Main").isPresent());

        // 4. Dependency Graph
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        assertEquals(3, graph.vertexCount());

        // Check dependencies: Main -> UserService and UserService -> User
        assertTrue(graph.outgoing("Main").contains("UserService"),
            "Main must depend on UserService (via local instantiation in main)");
        assertTrue(graph.outgoing("UserService").contains("User"),
            "UserService must depend on User (via field instantiation)");
        assertFalse(graph.outgoing("User").contains("UserService"),
            "User must not depend on UserService");

        // 5. Metrics calculation
        MetricsEngine metricsEngine = new MetricsEngine();
        MetricsEngine.ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        assertEquals(3, metrics.totalComponents);
        assertEquals(2, metrics.totalDependencies);
        assertEquals(0, metrics.cycleCount, "Should have 0 circular dependencies");
        assertTrue(metrics.architectureHealthScore >= 80.0, "Healthy 3-class linear architecture should score >= 80");

        // 6. Smell Detection
        SmellDetector smellDetector = new SmellDetector();
        List<SmellDetector.Smell> smells = smellDetector.detect(metrics, graph);
        for (SmellDetector.Smell s : smells) {
            System.out.println("DETECTED SMELL: " + s.type + " on " + s.componentId + ": " + s.evidence);
        }
        assertTrue(smells.isEmpty(), "Simple 3-class clean project must not have invented architectural smells: " + smells);

        // 7. ML Risk Prediction
        RiskPredictionService riskService = RiskPredictionService.loadDefault();
        assertNotNull(riskService);

        for (var cm : metrics.perComponent.values()) {
            RiskPrediction pred = riskService.predict(cm, 0);
            assertNotNull(pred);
            assertEquals("LOW", pred.riskLabel, "Clean component in small project should predict LOW risk");
            assertTrue(pred.confidence >= 0.50, "Confidence should be >= 50%");
            assertTrue(pred.modelUsed.contains("RandomForest"), "Model must be RandomForest");
        }

        // 8. Full Pipeline run
        Path workDir = tempDir.resolve("work");
        AnalyzerPipeline.PipelineResult result = pipeline.run(zipFile, workDir, "Phase4Demo", ConstraintSet.defaults());

        assertNotNull(result);
        assertEquals(3, result.model.fileCount());
        assertEquals(3, result.model.componentCount());
        assertNotNull(result.finalZipPath);
        assertTrue(Files.exists(result.finalZipPath));
        assertTrue(Files.exists(zipFile), "Original uploaded ZIP must be preserved untouched");
    }

    @Test
    void testMalformedZipFailsGracefully(@TempDir Path tempDir) throws Exception {
        Path corruptZip = tempDir.resolve("corrupt.zip");
        Files.writeString(corruptZip, "THIS IS NOT A VALID ZIP FILE HEADER OR CONTENT");

        Path workDir = tempDir.resolve("work-corrupt");
        Exception exception = assertThrows(Exception.class, () ->
            pipeline.run(corruptZip, workDir, "CorruptTest", ConstraintSet.defaults())
        );
        assertNotNull(exception.getMessage());
    }

    @Test
    void testEmptyProjectZipHandlesGracefully(@TempDir Path tempDir) throws Exception {
        Path emptyZip = tempDir.resolve("empty-project.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(emptyZip.toFile()))) {
            addZipEntry(zos, "README.txt", "No code in this repository.");
        }

        Path workDir = tempDir.resolve("work-empty");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            pipeline.run(emptyZip, workDir, "EmptyTest", ConstraintSet.defaults())
        );
        assertTrue(ex.getMessage().contains("No Java source files were found"),
            "Empty projects with no Java files must fail with clear descriptive message");
    }

    @Test
    void testInvalidJavaSyntaxRecordsParseErrors(@TempDir Path tempDir) throws Exception {
        Path brokenZip = tempDir.resolve("broken-syntax.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(brokenZip.toFile()))) {
            addZipEntry(zos, "src/Broken.java", "public class Broken { void unclosedMethod( }");
        }

        Path extractDir = tempDir.resolve("extracted-broken");
        ZipExtractor extractor = new ZipExtractor();
        extractor.extract(brokenZip, extractDir);

        ProjectDiscovery discovery = new ProjectDiscovery(true);
        ProjectModel model = discovery.discoverAndParse(extractDir, "BrokenTest");

        // The syntax error was trapped and recorded, never fabricated into fake AST nodes
        assertTrue(model.hasParseErrors(), "Broken syntax must be recorded as parse error");
        assertTrue(model.getParseErrors().containsKey("src/Broken.java"));
        assertTrue(model.getParseErrors().get("src/Broken.java").contains("Syntax errors"));
        assertEquals(0, model.componentCount(), "Broken file must not fabricate components");
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
