package com.archanalyzer.pipeline;

import com.archanalyzer.model.ProjectModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectDiscoveryTest {

    private ProjectDiscovery discovery;

    @BeforeEach
    void setUp() {
        discovery = new ProjectDiscovery(true);
    }

    @Test
    void testDiscoverValidJavaOnlyProject(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/main/java/com/example/service");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("GreetingService.java"),
            "package com.example.service;\n" +
            "public class GreetingService {\n" +
            "    public String greet(String name) { return \"Hello \" + name; }\n" +
            "}");

        ProjectModel model = discovery.discoverAndParse(tempDir, "TestProject");

        assertEquals(1, model.fileCount());
        assertEquals(1, model.componentCount());
        assertTrue(model.getComponent("com.example.service.GreetingService").isPresent());
        assertEquals(0, model.getExcludedNonJavaFiles().size());
        assertFalse(model.isMixedLanguage());
        assertFalse(model.hasParseErrors());
    }

    @Test
    void testMixedLanguageProjectAnalyzesJavaOnlyAndPreservesNonJava(@TempDir Path tempDir) throws IOException {
        Path javaPkg = tempDir.resolve("src/main/java/com/example");
        Path pyDir = tempDir.resolve("scripts");
        Path jsDir = tempDir.resolve("frontend");
        Files.createDirectories(javaPkg);
        Files.createDirectories(pyDir);
        Files.createDirectories(jsDir);

        // Java source
        Files.writeString(javaPkg.resolve("OrderService.java"),
            "package com.example;\n" +
            "public class OrderService {\n" +
            "    public void processOrder() {}\n" +
            "}");

        // Non-Java programming language source files
        Files.writeString(pyDir.resolve("worker.py"), "print('hello from python')\n");
        Files.writeString(jsDir.resolve("app.js"), "console.log('hello from js');\n");
        Files.writeString(jsDir.resolve("types.ts"), "export interface User { id: number; }\n");

        // Non-source resources
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Files.writeString(tempDir.resolve("application.properties"), "server.port=8080");

        ProjectModel model = discovery.discoverAndParse(tempDir, "MixedProject");

        // Only Java source is analyzed into components and files
        assertEquals(1, model.fileCount());
        assertEquals(1, model.componentCount());
        assertTrue(model.getComponent("com.example.OrderService").isPresent());

        // Non-Java source files are tracked as excluded
        assertTrue(model.isMixedLanguage());
        assertEquals(3, model.getExcludedNonJavaFiles().size());
        assertTrue(model.getExcludedLanguages().contains("python"));
        assertTrue(model.getExcludedLanguages().contains("javascript"));
        assertTrue(model.getExcludedLanguages().contains("typescript"));

        // Files remain preserved in the filesystem (not deleted)
        assertTrue(Files.exists(pyDir.resolve("worker.py")));
        assertTrue(Files.exists(jsDir.resolve("app.js")));
        assertTrue(Files.exists(jsDir.resolve("types.ts")));
        assertTrue(Files.exists(tempDir.resolve("pom.xml")));
    }

    @Test
    void testRejectPythonOnlyProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("main.py"), "print('python only')");
        Files.writeString(tempDir.resolve("utils.py"), "def foo(): pass");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            discovery.discoverAndParse(tempDir, "PythonProject"));

        assertEquals(ProjectDiscovery.NO_JAVA_FILES_MESSAGE, ex.getMessage());
    }

    @Test
    void testRejectJavaScriptOnlyProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("index.js"), "console.log('js only');");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            discovery.discoverAndParse(tempDir, "JsProject"));

        assertEquals(ProjectDiscovery.NO_JAVA_FILES_MESSAGE, ex.getMessage());
    }

    @Test
    void testRejectTypeScriptOnlyProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("app.ts"), "const x: number = 42;");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            discovery.discoverAndParse(tempDir, "TsProject"));

        assertEquals(ProjectDiscovery.NO_JAVA_FILES_MESSAGE, ex.getMessage());
    }

    @Test
    void testRejectProjectWithNoJavaFiles(@TempDir Path tempDir) throws IOException {
        // Project with only configs, docs, images
        Files.writeString(tempDir.resolve("README.md"), "# Just a readme");
        Files.writeString(tempDir.resolve("config.json"), "{\"app\":\"demo\"}");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            discovery.discoverAndParse(tempDir, "DocsOnlyProject"));

        assertEquals(ProjectDiscovery.NO_JAVA_FILES_MESSAGE, ex.getMessage());
    }

    @Test
    void testNonSourceResourcesNotMisclassifiedAsJava(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "public class App {}");
        Files.writeString(tempDir.resolve("data.xml"), "<data/>");
        Files.writeString(tempDir.resolve("schema.sql"), "CREATE TABLE test;");
        Files.writeString(tempDir.resolve("config.yaml"), "key: value");

        ProjectModel model = discovery.discoverAndParse(tempDir, "AppWithResources");

        assertEquals(1, model.fileCount());
        assertEquals(1, model.componentCount());
        // Resources are not added to excluded non-Java programming languages
        assertEquals(0, model.getExcludedNonJavaFiles().size());
        assertFalse(model.isMixedLanguage());
    }

    @Test
    void testJavaFilesInNestedPackagesDiscovered(@TempDir Path tempDir) throws IOException {
        Path deepPkg = tempDir.resolve("src/main/java/com/company/app/sub1/sub2/sub3");
        Files.createDirectories(deepPkg);
        Files.writeString(deepPkg.resolve("DeepService.java"),
            "package com.company.app.sub1.sub2.sub3;\n" +
            "public class DeepService {\n" +
            "    public void run() {}\n" +
            "}");

        ProjectModel model = discovery.discoverAndParse(tempDir, "DeepProject");

        assertEquals(1, model.fileCount());
        assertTrue(model.getComponent("com.company.app.sub1.sub2.sub3.DeepService").isPresent());
    }

    @Test
    void testBuildAndVcsDirectoriesSkipped(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src/main/java");
        Path target = tempDir.resolve("target/classes");
        Path git = tempDir.resolve(".git/objects");
        Path nodeModules = tempDir.resolve("node_modules/pkg");

        Files.createDirectories(src);
        Files.createDirectories(target);
        Files.createDirectories(git);
        Files.createDirectories(nodeModules);

        Files.writeString(src.resolve("Main.java"), "public class Main {}");
        Files.writeString(target.resolve("Compiled.java"), "public class Compiled {}");
        Files.writeString(git.resolve("Commit.java"), "public class Commit {}");
        Files.writeString(nodeModules.resolve("Dep.java"), "public class Dep {}");

        ProjectModel model = discovery.discoverAndParse(tempDir, "SkipTest");

        assertEquals(1, model.fileCount());
        assertTrue(model.getComponent("Main").isPresent());
        assertFalse(model.getComponent("Compiled").isPresent());
        assertFalse(model.getComponent("Commit").isPresent());
        assertFalse(model.getComponent("Dep").isPresent());
    }

    @Test
    void testJavaSyntaxErrorAccuratelyReported(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);

        Files.writeString(src.resolve("Valid.java"), "public class Valid {}");
        Files.writeString(src.resolve("Broken.java"), "public class Broken { incomplete syntax !!@@##");

        ProjectModel model = discovery.discoverAndParse(tempDir, "SyntaxErrProject");

        assertTrue(model.hasParseErrors());
        assertEquals(1, model.getParseErrors().size());
        assertTrue(model.getParseErrors().keySet().stream().anyMatch(k -> k.endsWith("Broken.java")));
        // Valid file was still analyzed
        assertTrue(model.getComponent("Valid").isPresent());
    }

    @Test
    void testDetectBuildToolInNestedDirectory(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("my-app");
        Files.createDirectories(nested.resolve("src/main/java"));
        Files.writeString(nested.resolve("pom.xml"), "<project></project>");
        Files.writeString(nested.resolve("src/main/java/App.java"), "public class App {}");

        ProjectModel model = discovery.discoverAndParse(tempDir, "NestedMavenApp");
        assertEquals("maven", model.getBuildInfo().get("buildTool"));
        assertEquals(1, model.fileCount());
    }
}
