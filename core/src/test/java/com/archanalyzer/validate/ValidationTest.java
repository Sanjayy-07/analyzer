package com.archanalyzer.validate;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationTest {

    private BuildValidator buildValidator;
    private StructuralValidator structuralValidator;
    private ValidationAggregator aggregator;

    @BeforeEach
    void setUp() {
        buildValidator = new BuildValidator();
        structuralValidator = new StructuralValidator();
        aggregator = new ValidationAggregator();
    }

    @Test
    void testBuildValidatorCompilesValidJavaCode(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("Calculator.java"),
            "package com.example;\n" +
            "public class Calculator {\n" +
            "    public int add(int a, int b) { return a + b; }\n" +
            "}");

        BuildValidator.BuildResult result = buildValidator.compile(tempDir, List.of());

        assertEquals(BuildValidator.Status.PASS, result.status);
        assertEquals(1, result.filesCompiled);
        assertTrue(result.errors.isEmpty());
    }

    @Test
    void testBuildValidatorDetectsCompilationError(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("BrokenClass.java"),
            "package com.example;\n" +
            "public class BrokenClass {\n" +
            "    public int brokenMethod() {\n" +
            "        return \"not an int\"; // type mismatch\n" +
            "    }\n" +
            "}");

        BuildValidator.BuildResult result = buildValidator.compile(tempDir, List.of());

        assertEquals(BuildValidator.Status.FAIL, result.status);
        assertFalse(result.errors.isEmpty());
    }

    @Test
    void testStructuralValidatorDetectsImprovement() {
        ProjectMetrics before = new ProjectMetrics();
        before.architectureHealthScore = 70.0;
        before.dependencyScore = 30.0;
        before.cycleCount = 2;

        Baseline baseline = Baseline.capture(before);

        ProjectMetrics after = new ProjectMetrics();
        after.architectureHealthScore = 80.0;
        after.dependencyScore = 20.0;
        after.cycleCount = 1;

        StructuralValidator.ComparisonResult comp = structuralValidator.compare(baseline, after);

        assertTrue(comp.improved);
        assertFalse(comp.regressed);
        assertEquals(10.0, comp.architectureHealthDelta);
        assertEquals(1, comp.cyclesBefore - comp.cyclesAfter);
    }

    @Test
    void testStructuralValidatorDetectsRegression() {
        ProjectMetrics before = new ProjectMetrics();
        before.architectureHealthScore = 80.0;
        before.dependencyScore = 20.0;
        before.cycleCount = 1;

        Baseline baseline = Baseline.capture(before);

        ProjectMetrics after = new ProjectMetrics();
        after.architectureHealthScore = 65.0; // regressed
        after.dependencyScore = 35.0;         // worse
        after.cycleCount = 3;                 // more cycles

        StructuralValidator.ComparisonResult comp = structuralValidator.compare(baseline, after);

        assertFalse(comp.improved);
        assertTrue(comp.regressed);
    }

    @Test
    void testValidationAggregatorFailOnBuildError() {
        BuildValidator.BuildResult build = new BuildValidator.BuildResult();
        build.status = BuildValidator.Status.FAIL;
        build.errors.add("error: ';' expected");

        StructuralValidator.ComparisonResult structural = new StructuralValidator.ComparisonResult();
        structural.improved = true;

        ValidationAggregator.ValidationResult val = aggregator.aggregate(build, structural);

        assertEquals(ValidationAggregator.Verdict.FAIL, val.verdict);
        assertTrue(val.summary.contains("Build failed after refactoring"));
    }

    @Test
    void testValidationAggregatorPassWhenBothSucceed() {
        BuildValidator.BuildResult build = new BuildValidator.BuildResult();
        build.status = BuildValidator.Status.PASS;

        StructuralValidator.ComparisonResult structural = new StructuralValidator.ComparisonResult();
        structural.improved = true;
        structural.regressed = false;

        ValidationAggregator.ValidationResult val = aggregator.aggregate(build, structural);

        assertEquals(ValidationAggregator.Verdict.PASS, val.verdict);
        assertTrue(val.summary.contains("Build succeeded"));
    }
}
