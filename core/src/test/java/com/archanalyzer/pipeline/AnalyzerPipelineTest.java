package com.archanalyzer.pipeline;

import com.archanalyzer.optimization.ConstraintSet;
import com.archanalyzer.refactor.RefactoringChange;
import com.archanalyzer.refactor.RefactoringEngine;
import com.archanalyzer.validate.BuildValidator;
import com.archanalyzer.validate.StructuralValidator;
import com.archanalyzer.validate.ValidationAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzerPipelineTest {

    private AnalyzerPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new AnalyzerPipeline(true);
    }

    @Test
    void testFullPipelineOnJavaProject(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-app");
        Path pkg = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        // Service with multiple methods so Interface Extraction can trigger
        StringBuilder serviceCode = new StringBuilder();
        serviceCode.append("package com.example;\n");
        serviceCode.append("public class InventoryService {\n");
        for (int i = 1; i <= 25; i++) {
            serviceCode.append("    public void method").append(i).append("() {}\n");
        }
        serviceCode.append("}\n");

        Files.writeString(pkg.resolve("InventoryService.java"), serviceCode.toString());
        Files.writeString(projectDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        AnalyzerPipeline.PipelineResult result = pipeline.run(projectDir, workDir, "TestApp", ConstraintSet.defaults());

        assertNotNull(result);
        assertNotNull(result.model);
        assertEquals(1, result.model.fileCount());
        assertNotNull(result.metricsBefore);
        assertNotNull(result.riskPredictions);
        assertNotNull(result.rankedCandidates);
        assertNotNull(result.reportMarkdown);
        assertNotNull(result.finalZipPath);
        assertTrue(Files.exists(result.finalZipPath));

        // Original source project directory was not modified directly (isolation check)
        String originalSource = Files.readString(pkg.resolve("InventoryService.java"));
        assertFalse(originalSource.contains("implements IInventoryService"),
            "Original project directory must be preserved intact without direct modification");
    }

    @Test
    void testMixedLanguageProjectPreservesNonJavaFilesInZip(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("mixed-app");
        Path javaPkg = projectDir.resolve("src/main/java/com/example");
        Path pyDir = projectDir.resolve("scripts");
        Files.createDirectories(javaPkg);
        Files.createDirectories(pyDir);

        Files.writeString(javaPkg.resolve("AppService.java"),
            "package com.example;\npublic class AppService {\n    public void doWork() {}\n}\n");
        Files.writeString(pyDir.resolve("task.py"), "print('hello from python')\n");
        Files.writeString(projectDir.resolve("config.json"), "{\"env\":\"prod\"}\n");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        AnalyzerPipeline.PipelineResult result = pipeline.run(projectDir, workDir, "MixedApp", ConstraintSet.defaults());

        // Java source analyzed
        assertEquals(1, result.model.fileCount());
        assertTrue(result.model.isMixedLanguage());
        assertEquals(1, result.model.getExcludedNonJavaFiles().size());

        // Packaged ZIP contains both Java and preserved non-Java files
        assertTrue(Files.exists(result.finalZipPath));
        boolean foundPython = false;
        boolean foundJson = false;
        boolean foundJava = false;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(result.finalZipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith("task.py")) foundPython = true;
                if (name.endsWith("config.json")) foundJson = true;
                if (name.endsWith("AppService.java")) foundJava = true;
                zis.closeEntry();
            }
        }

        assertTrue(foundJava, "Java source must be in output ZIP");
        assertTrue(foundPython, "Preserved Python script must be in output ZIP");
        assertTrue(foundJson, "Preserved JSON config must be in output ZIP");
    }

    @Test
    void testRejectProjectWithNoJavaFilesThrowsException(@TempDir Path tempDir) {
        Path projectDir = tempDir.resolve("non-java-app");
        try {
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("main.py"), "print('hello')");
            Files.writeString(projectDir.resolve("app.js"), "console.log('hello')");
        } catch (IOException e) {
            fail(e);
        }

        Path workDir = tempDir.resolve("work");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            pipeline.run(projectDir, workDir, "NoJavaApp", ConstraintSet.defaults()));

        assertEquals(ProjectDiscovery.NO_JAVA_FILES_MESSAGE, ex.getMessage());
    }

    @Test
    void testPreviewOnlyCandidatesClearlyLabeled(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("demo-app");
        Path pkg = projectDir.resolve("src/main/java/com/demo");
        Files.createDirectories(pkg);

        // Create 2 classes with a circular dependency
        Files.writeString(pkg.resolve("ServiceA.java"),
            "package com.demo;\n" +
            "public class ServiceA {\n" +
            "    public ServiceB b;\n" +
            "}\n");
        Files.writeString(pkg.resolve("ServiceB.java"),
            "package com.demo;\n" +
            "public class ServiceB {\n" +
            "    public ServiceA a;\n" +
            "}\n");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        AnalyzerPipeline.PipelineResult result = pipeline.run(projectDir, workDir, "DemoApp", ConstraintSet.defaults());

        // Any Dependency Inversion candidate must be labeled "Preview Only — Not Applied"
        for (var c : result.rankedCandidates) {
            if ("Dependency Inversion".equals(c.strategyName)) {
                assertEquals("Preview Only — Not Applied", c.status);
            }
        }
    }

    @Test
    void testRejectedRefactoringRollsBackWorkingCopy(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-app");
        Path pkg = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        StringBuilder serviceCode = new StringBuilder();
        serviceCode.append("package com.example;\n");
        serviceCode.append("public class InventoryService {\n");
        for (int i = 1; i <= 25; i++) {
            serviceCode.append("    public void method").append(i).append("() {}\n");
        }
        serviceCode.append("}\n");

        Files.writeString(pkg.resolve("InventoryService.java"), serviceCode.toString());
        Files.writeString(projectDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        ValidationAggregator rejectingAggregator = new ValidationAggregator() {
            @Override
            public ValidationResult aggregate(BuildValidator.BuildResult build, StructuralValidator.ComparisonResult structural) {
                ValidationResult r = new ValidationResult();
                r.verdict = Verdict.FAIL;
                r.buildResult = build;
                r.structuralComparison = structural;
                r.summary = "Forced rejection in test: architecture health regressed.";
                return r;
            }
        };

        AnalyzerPipeline rejectingPipeline = new AnalyzerPipeline(rejectingAggregator);
        AnalyzerPipeline.PipelineResult result = rejectingPipeline.run(projectDir, workDir, "TestApp", ConstraintSet.defaults());

        assertNotNull(result);
        assertNotNull(result.validation);
        assertEquals(ValidationAggregator.Verdict.FAIL, result.validation.verdict);
        assertNotNull(result.selectedCandidate);
        assertEquals("VALIDATED_FAIL", result.selectedCandidate.status);
        assertTrue(result.rankedCandidates.stream().anyMatch(c -> "VALIDATED_FAIL".equals(c.status)));
        assertTrue(result.rejectedCandidates.contains(result.selectedCandidate));

        // Assert working copy (refactoredRoot) does NOT contain extracted interface and class is unmodified
        assertNotNull(result.refactoredProjectRoot);
        assertFalse(Files.exists(result.refactoredProjectRoot.resolve("src/main/java/com/example/IInventoryService.java")),
            "Refactored working copy must not contain extracted interface after rejection");
        String refactoredContent = Files.readString(result.refactoredProjectRoot.resolve("src/main/java/com/example/InventoryService.java"));
        assertFalse(refactoredContent.contains("implements IInventoryService"),
            "Refactored working copy class header must not implement extracted interface after rejection");

        // Assert original source untouched
        String originalSource = Files.readString(pkg.resolve("InventoryService.java"));
        assertFalse(originalSource.contains("implements IInventoryService"),
            "Input project source must remain intact");

        // Assert finalZipPath ends with -original.zip
        assertNotNull(result.finalZipPath);
        assertTrue(result.finalZipPath.getFileName().toString().endsWith("-original.zip"),
            "Deliverable ZIP must end with -original.zip upon candidate rejection");

        // Open and inspect ZIP: does not contain interface, contains original class
        boolean foundInterfaceInZip = false;
        boolean foundServiceInZip = false;
        String zipServiceContent = null;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(result.finalZipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith("IInventoryService.java")) {
                    foundInterfaceInZip = true;
                }
                if (name.endsWith("InventoryService.java")) {
                    foundServiceInZip = true;
                    zipServiceContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }
        assertFalse(foundInterfaceInZip, "Output ZIP must not contain extracted interface after rejection");
        assertTrue(foundServiceInZip, "Output ZIP must contain original service class");
        assertNotNull(zipServiceContent);
        assertFalse(zipServiceContent.contains("implements IInventoryService"),
            "Service class in output ZIP must not implement extracted interface");

        // Report contains rejection rollback text and verdict FAIL
        assertNotNull(result.reportMarkdown);
        assertTrue(result.reportMarkdown.contains("REJECTED & ROLLED BACK"),
            "Report must state candidate was rejected and rolled back");
        assertTrue(result.reportMarkdown.contains("FAIL"),
            "Report must show FAIL verdict");
    }

    @Test
    void testAcceptedRefactoringPreservedInFinalDeliverable(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-app");
        Path pkg = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        StringBuilder serviceCode = new StringBuilder();
        serviceCode.append("package com.example;\n");
        serviceCode.append("public class InventoryService {\n");
        for (int i = 1; i <= 25; i++) {
            serviceCode.append("    public void method").append(i).append("() {}\n");
        }
        serviceCode.append("}\n");

        Files.writeString(pkg.resolve("InventoryService.java"), serviceCode.toString());
        Files.writeString(projectDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        ValidationAggregator passingAggregator = new ValidationAggregator() {
            @Override
            public ValidationResult aggregate(BuildValidator.BuildResult build, StructuralValidator.ComparisonResult structural) {
                ValidationResult r = new ValidationResult();
                r.verdict = Verdict.PASS;
                r.buildResult = build;
                r.structuralComparison = structural;
                r.summary = "Forced pass in test: refactoring verified.";
                return r;
            }
        };

        AnalyzerPipeline passingPipeline = new AnalyzerPipeline(passingAggregator);
        AnalyzerPipeline.PipelineResult result = passingPipeline.run(projectDir, workDir, "TestApp", ConstraintSet.defaults());

        assertNotNull(result);
        assertNotNull(result.validation);
        assertEquals(ValidationAggregator.Verdict.PASS, result.validation.verdict);
        assertNotNull(result.selectedCandidate);
        assertEquals("VALIDATED_PASS", result.selectedCandidate.status);
        assertTrue(result.rejectedCandidates.isEmpty());

        // Working copy contains extracted interface and modified class
        assertNotNull(result.refactoredProjectRoot);
        assertTrue(Files.exists(result.refactoredProjectRoot.resolve("src/main/java/com/example/IInventoryService.java")),
            "Refactored working copy must contain extracted interface after acceptance");
        String refactoredContent = Files.readString(result.refactoredProjectRoot.resolve("src/main/java/com/example/InventoryService.java"));
        assertTrue(refactoredContent.contains("implements IInventoryService"),
            "Refactored working copy class header must implement extracted interface");

        // ZIP ends with -refactored.zip
        assertNotNull(result.finalZipPath);
        assertTrue(result.finalZipPath.getFileName().toString().endsWith("-refactored.zip"),
            "Deliverable ZIP must end with -refactored.zip upon candidate acceptance");

        // Open and inspect ZIP: contains interface and modified class
        boolean foundInterfaceInZip = false;
        boolean foundServiceInZip = false;
        String zipServiceContent = null;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(result.finalZipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith("IInventoryService.java")) {
                    foundInterfaceInZip = true;
                }
                if (name.endsWith("InventoryService.java")) {
                    foundServiceInZip = true;
                    zipServiceContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }
        assertTrue(foundInterfaceInZip, "Output ZIP must contain extracted interface after acceptance");
        assertTrue(foundServiceInZip, "Output ZIP must contain service class");
        assertNotNull(zipServiceContent);
        assertTrue(zipServiceContent.contains("implements IInventoryService"),
            "Service class in output ZIP must implement extracted interface");

        // Report contains PASS
        assertNotNull(result.reportMarkdown);
        assertTrue(result.reportMarkdown.contains("PASS"),
            "Report must show PASS verdict");
    }

    @Test
    void testRefactoringExecutionFailureRollsBackWorkingCopy(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-app");
        Path pkg = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        StringBuilder serviceCode = new StringBuilder();
        serviceCode.append("package com.example;\n");
        serviceCode.append("public class InventoryService {\n");
        for (int i = 1; i <= 25; i++) {
            serviceCode.append("    public void method").append(i).append("() {}\n");
        }
        serviceCode.append("}\n");

        Files.writeString(pkg.resolve("InventoryService.java"), serviceCode.toString());
        Files.writeString(projectDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        RefactoringEngine failingEngine = new RefactoringEngine() {
            @Override
            public List<RefactoringChange> extractInterface(
                    Path projectRoot, Path targetClassFile, String targetClassSimpleName) throws IOException {
                // Introduce dirty file before failure to simulate mid-execution crash
                Files.writeString(projectRoot.resolve("partial_state.tmp"), "corrupt partial state");
                throw new IOException("Simulated engine failure during extraction");
            }
        };

        AnalyzerPipeline failingPipeline = new AnalyzerPipeline(failingEngine);
        AnalyzerPipeline.PipelineResult result = failingPipeline.run(projectDir, workDir, "TestApp", ConstraintSet.defaults());

        assertNotNull(result);
        assertNotNull(result.selectedCandidate);
        assertEquals("FAILED", result.selectedCandidate.status);
        assertTrue(result.selectedCandidate.plannedChanges.stream().anyMatch(p -> p.startsWith("FAILED: Simulated engine failure")));

        // Assert working copy was rolled back: partial file removed, class unchanged, no interface
        assertNotNull(result.refactoredProjectRoot);
        assertFalse(Files.exists(result.refactoredProjectRoot.resolve("partial_state.tmp")),
            "Dirty file from failed refactoring must be cleaned up on rollback");
        assertFalse(Files.exists(result.refactoredProjectRoot.resolve("src/main/java/com/example/IInventoryService.java")),
            "Extracted interface must not exist in rolled back working copy");
        String refactoredContent = Files.readString(result.refactoredProjectRoot.resolve("src/main/java/com/example/InventoryService.java"));
        assertFalse(refactoredContent.contains("implements IInventoryService"),
            "Service class must remain unmodified");

        // Assert final ZIP packages original project
        assertNotNull(result.finalZipPath);
        assertTrue(result.finalZipPath.getFileName().toString().endsWith("-original.zip"),
            "Deliverable ZIP must end with -original.zip on engine failure");

        boolean foundPartialInZip = false;
        boolean foundInterfaceInZip = false;
        boolean foundServiceInZip = false;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(result.finalZipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith("partial_state.tmp")) foundPartialInZip = true;
                if (name.endsWith("IInventoryService.java")) foundInterfaceInZip = true;
                if (name.endsWith("InventoryService.java")) foundServiceInZip = true;
                zis.closeEntry();
            }
        }
        assertFalse(foundPartialInZip, "Output ZIP must not contain dirty file");
        assertFalse(foundInterfaceInZip, "Output ZIP must not contain interface");
        assertTrue(foundServiceInZip, "Output ZIP must contain original service");
    }

    @Test
    void testPreservedNonJavaFilesAndDirectoryStructureInRejectedDeliverable(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-app");
        Path pkg = projectDir.resolve("src/main/java/com/example");
        Path configDir = projectDir.resolve("config");
        Path scriptsDir = projectDir.resolve("scripts");
        Files.createDirectories(pkg);
        Files.createDirectories(configDir);
        Files.createDirectories(scriptsDir);

        StringBuilder serviceCode = new StringBuilder();
        serviceCode.append("package com.example;\n");
        serviceCode.append("public class InventoryService {\n");
        for (int i = 1; i <= 25; i++) {
            serviceCode.append("    public void method").append(i).append("() {}\n");
        }
        serviceCode.append("}\n");

        Files.writeString(pkg.resolve("InventoryService.java"), serviceCode.toString());
        Files.writeString(projectDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
        Files.writeString(configDir.resolve("settings.json"), "{\"app\":\"test\",\"active\":true}");
        Files.writeString(configDir.resolve("database.properties"), "db.user=admin\ndb.timeout=30\n");
        Files.writeString(scriptsDir.resolve("deploy.py"), "print('deploying service')\n");

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        ValidationAggregator rejectingAggregator = new ValidationAggregator() {
            @Override
            public ValidationResult aggregate(BuildValidator.BuildResult build, StructuralValidator.ComparisonResult structural) {
                ValidationResult r = new ValidationResult();
                r.verdict = Verdict.FAIL;
                r.buildResult = build;
                r.structuralComparison = structural;
                r.summary = "Forced rejection: metrics regressed.";
                return r;
            }
        };

        AnalyzerPipeline rejectingPipeline = new AnalyzerPipeline(rejectingAggregator);
        AnalyzerPipeline.PipelineResult result = rejectingPipeline.run(projectDir, workDir, "TestApp", ConstraintSet.defaults());

        assertEquals(ValidationAggregator.Verdict.FAIL, result.validation.verdict);
        assertTrue(result.finalZipPath.getFileName().toString().endsWith("-original.zip"));

        // Non-Java files preserved intact in rolled-back refactoredProjectRoot
        assertEquals("{\"app\":\"test\",\"active\":true}",
            Files.readString(result.refactoredProjectRoot.resolve("config/settings.json")));
        assertEquals("db.user=admin\ndb.timeout=30\n",
            Files.readString(result.refactoredProjectRoot.resolve("config/database.properties")));
        assertEquals("print('deploying service')\n",
            Files.readString(result.refactoredProjectRoot.resolve("scripts/deploy.py")));

        // Verify ZIP contents and readability with standard ZipInputStream
        boolean foundJson = false;
        boolean foundProps = false;
        boolean foundPy = false;
        boolean foundJava = false;
        boolean foundInterface = false;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(result.finalZipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                byte[] bytes = zis.readAllBytes();
                if (name.endsWith("config/settings.json")) {
                    foundJson = true;
                    assertEquals("{\"app\":\"test\",\"active\":true}", new String(bytes, StandardCharsets.UTF_8));
                }
                if (name.endsWith("config/database.properties")) {
                    foundProps = true;
                    assertEquals("db.user=admin\ndb.timeout=30\n", new String(bytes, StandardCharsets.UTF_8));
                }
                if (name.endsWith("scripts/deploy.py")) {
                    foundPy = true;
                    assertEquals("print('deploying service')\n", new String(bytes, StandardCharsets.UTF_8));
                }
                if (name.endsWith("InventoryService.java")) {
                    foundJava = true;
                }
                if (name.endsWith("IInventoryService.java")) {
                    foundInterface = true;
                }
                zis.closeEntry();
            }
        }

        assertTrue(foundJson, "ZIP must contain settings.json");
        assertTrue(foundProps, "ZIP must contain database.properties");
        assertTrue(foundPy, "ZIP must contain deploy.py");
        assertTrue(foundJava, "ZIP must contain InventoryService.java");
        assertFalse(foundInterface, "ZIP must NOT contain IInventoryService.java");
    }
}
