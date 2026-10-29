package com.archanalyzer.parser;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;
import com.archanalyzer.model.ProjectModel.DependencyEdge;
import com.archanalyzer.pipeline.ProjectDiscovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MultiLanguageParserTest {

    private JavaScriptLanguageAdapter jsAdapter;
    private TypeScriptLanguageAdapter tsAdapter;
    private PythonLanguageAdapter pyAdapter;

    @BeforeEach
    void setUp() {
        jsAdapter = new JavaScriptLanguageAdapter();
        tsAdapter = new TypeScriptLanguageAdapter();
        pyAdapter = new PythonLanguageAdapter();
    }

    @Test
    void testJavaScriptParsingExtractsComponentsAndImports(@TempDir Path tempDir) throws IOException {
        Path jsFile = tempDir.resolve("paymentService.js");
        String jsCode =
            "import { notifyCustomer } from './notificationService';\n" +
            "const axios = require('axios');\n\n" +
            "export class PaymentGateway {\n" +
            "    constructor(apiKey) {\n" +
            "        this.apiKey = apiKey;\n" +
            "    }\n" +
            "    processCharge(amount) {\n" +
            "        if (amount <= 0) throw new Error('Invalid amount');\n" +
            "        return axios.post('/api/charge', { amount });\n" +
            "    }\n" +
            "}\n\n" +
            "export const formatCurrency = (val) => '$' + val;\n" +
            "app.get('/api/payments', (req, res) => res.json([]));\n";

        Files.writeString(jsFile, jsCode);

        ProjectModel model = new ProjectModel("JsProject");
        jsAdapter.parseInto(tempDir, jsFile, model);

        assertTrue(model.fileCount() >= 1);
        assertTrue(model.getFiles().stream().anyMatch(f -> f.getLanguage().equals("javascript")));

        // Verify module node
        assertTrue(model.getComponent("paymentService").isPresent(), "Module node must be registered");

        // Verify class node
        assertTrue(model.getComponent("paymentService.PaymentGateway").isPresent(), "PaymentGateway class must be extracted");
        ComponentNode classNode = model.getComponent("paymentService.PaymentGateway").get();
        assertEquals(ComponentNode.Kind.CLASS, classNode.getKind());
        assertTrue(classNode.getCyclomaticComplexity() >= 1);

        // Verify function node
        assertTrue(model.getComponent("paymentService.formatCurrency").isPresent(), "formatCurrency function must be extracted");

        // Verify Express route node
        assertTrue(model.getComponent("paymentService:GET /api/payments").isPresent(), "Route node must be registered");

        // Verify dependencies
        assertTrue(model.getDependencies().stream().anyMatch(e -> e.getTo().contains("notificationService")),
            "Import of notificationService must create an IMPORT edge");
        assertTrue(model.getDependencies().stream().anyMatch(e -> e.getTo().equals("npm:axios")),
            "Require of axios must create an external npm dependency");
        assertTrue(model.getDependencies().stream().anyMatch(e -> e.getTo().equals("API:/api/charge")),
            "Axios API call must register API dependency");
    }

    @Test
    void testTypeScriptParsingExtractsInterfacesAndTypes(@TempDir Path tempDir) throws IOException {
        Path tsFile = tempDir.resolve("userModel.ts");
        String tsCode =
            "import { BaseEntity } from './base';\n\n" +
            "export interface UserProfile extends BaseEntity {\n" +
            "    id: string;\n" +
            "    email: string;\n" +
            "}\n\n" +
            "export type UserStatus = 'ACTIVE' | 'SUSPENDED';\n\n" +
            "export class UserService implements UserProfile {\n" +
            "    id = '1';\n" +
            "    email = 'user@example.com';\n" +
            "    getUser() { return this.email; }\n" +
            "}\n";

        Files.writeString(tsFile, tsCode);

        ProjectModel model = new ProjectModel("TsProject");
        tsAdapter.parseInto(tempDir, tsFile, model);

        assertTrue(model.getComponent("userModel.UserProfile").isPresent(), "TypeScript interface must be extracted");
        ComponentNode iface = model.getComponent("userModel.UserProfile").get();
        assertEquals(ComponentNode.Kind.INTERFACE, iface.getKind());

        assertTrue(model.getComponent("userModel.UserStatus").isPresent(), "TypeScript type alias must be extracted");

        assertTrue(model.getDependencies().stream().anyMatch(e -> e.getType() == DependencyEdge.Type.EXTENDS && e.getTo().equals("BaseEntity")),
            "Interface extension must be captured as EXTENDS dependency");
        assertTrue(model.getDependencies().stream().anyMatch(e -> e.getType() == DependencyEdge.Type.IMPLEMENTS && e.getTo().equals("UserProfile")),
            "Class implementation must be captured as IMPLEMENTS dependency");
    }

    @Test
    void testPythonParsingExtractsClassesAndFunctions(@TempDir Path tempDir) throws IOException {
        Path pyFile = tempDir.resolve("order_worker.py");
        String pyCode =
            "import os\n" +
            "from services.inventory import reserve_stock\n\n" +
            "class OrderProcessor(BaseWorker):\n" +
            "    def __init__(self, queue):\n" +
            "        self.queue = queue\n\n" +
            "    def process_order(self, order_id):\n" +
            "        if not order_id:\n" +
            "            return False\n" +
            "        return reserve_stock(order_id)\n\n" +
            "def calculate_tax(amount):\n" +
            "    return amount * 0.1\n";

        Files.writeString(pyFile, pyCode);

        ProjectModel model = new ProjectModel("PyProject");
        pyAdapter.parseInto(tempDir, pyFile, model);

        assertTrue(model.getComponent("order_worker").isPresent());
        assertTrue(model.getComponent("order_worker.OrderProcessor").isPresent());
        ComponentNode cls = model.getComponent("order_worker.OrderProcessor").get();
        assertEquals(ComponentNode.Kind.CLASS, cls.getKind());
        assertEquals("BaseWorker", cls.getExtendsClass());

        assertTrue(model.getComponent("order_worker.calculate_tax").isPresent());
        ComponentNode fn = model.getComponent("order_worker.calculate_tax").get();
        assertEquals(ComponentNode.Kind.FUNCTION, fn.getKind());

        assertTrue(model.getDependencies().stream().anyMatch(e -> e.getTo().equals("services.inventory")),
            "Python import must be recorded in dependency graph");
    }

    @Test
    void testMultiLanguageProjectDiscovery(@TempDir Path tempDir) throws IOException {
        Path javaDir = tempDir.resolve("src/main/java/com/app");
        Path jsDir = tempDir.resolve("frontend/src");
        Path pyDir = tempDir.resolve("workers");
        Path unsuppDir = tempDir.resolve("native");

        Files.createDirectories(javaDir);
        Files.createDirectories(jsDir);
        Files.createDirectories(pyDir);
        Files.createDirectories(unsuppDir);

        Files.writeString(javaDir.resolve("AppService.java"),
            "package com.app;\npublic class AppService {\n    public void run() {}\n}\n");
        Files.writeString(jsDir.resolve("App.jsx"),
            "export function App() { return <div className='app'>Hello</div>; }\n");
        Files.writeString(pyDir.resolve("tasks.py"),
            "def run_task():\n    pass\n");
        Files.writeString(unsuppDir.resolve("engine.cpp"),
            "#include <iostream>\nint main() { return 0; }\n");
        Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"multi-app\"}\n");

        ProjectDiscovery discovery = new ProjectDiscovery(false);
        ProjectModel model = discovery.discoverAndParse(tempDir, "PolyglotApp");

        // 3 supported source files parsed into model (Java, JS, Python)
        assertEquals(3, model.fileCount());
        assertTrue(model.getComponent("com.app.AppService").isPresent());
        assertTrue(model.getComponent("frontend/src/App.App").isPresent());
        assertTrue(model.getComponent("workers.tasks.run_task").isPresent());

        // engine.cpp preserved as unsupported language
        assertTrue(model.getExcludedNonJavaFiles().stream().anyMatch(f -> f.endsWith("engine.cpp")));
        assertTrue(model.getExcludedLanguages().contains("cpp"));

        // Language capabilities recorded accurately
        assertTrue(model.getLanguageCapabilities().containsKey("java"));
        assertTrue(model.getLanguageCapabilities().containsKey("javascript"));
        assertTrue(model.getLanguageCapabilities().containsKey("typescript"));
        assertTrue(model.getLanguageCapabilities().containsKey("python"));
        assertTrue(model.getLanguageCapabilities().containsKey("cpp"));
        assertEquals("Full", model.getLanguageCapabilities().get("java").getDepthSummary());
        assertEquals("Standard Analysis", model.getLanguageCapabilities().get("javascript").getDepthSummary());
        assertEquals("Preserved / Unsupported", model.getLanguageCapabilities().get("cpp").getDepthSummary());
    }
}
