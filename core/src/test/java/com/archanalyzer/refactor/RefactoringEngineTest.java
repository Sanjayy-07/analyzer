package com.archanalyzer.refactor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RefactoringEngineTest {

    private RefactoringEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RefactoringEngine();
    }

    @Test
    void testExtractInterfaceRealJavaSourceTransformation(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/com/example");
        Files.createDirectories(pkg);

        Path classFile = pkg.resolve("CustomerService.java");
        Files.writeString(classFile,
            "package com.example;\n\n" +
            "public class CustomerService {\n" +
            "    public String findCustomer(int id) {\n" +
            "        return \"Customer #\" + id;\n" +
            "    }\n" +
            "    public void saveCustomer(String name) {\n" +
            "        // save logic\n" +
            "    }\n" +
            "    private void internalHelper() {}\n" +
            "}\n");

        List<RefactoringChange> changes = engine.extractInterface(tempDir, classFile, "CustomerService");

        assertNotNull(changes);
        assertEquals(2, changes.size());

        // Interface file was created
        Path interfaceFile = pkg.resolve("ICustomerService.java");
        assertTrue(Files.exists(interfaceFile), "Interface ICustomerService.java must be created");

        String interfaceSource = Files.readString(interfaceFile);
        assertTrue(interfaceSource.contains("public interface ICustomerService"));
        assertTrue(interfaceSource.contains("String findCustomer(int id);"));
        assertTrue(interfaceSource.contains("void saveCustomer(String name);"));
        assertFalse(interfaceSource.contains("internalHelper"), "Private methods must not be in interface");

        // Class was rewritten to implement interface
        String modifiedClass = Files.readString(classFile);
        assertTrue(modifiedClass.contains("public class CustomerService implements ICustomerService {"));
    }

    @Test
    void testExtractInterfaceWithExistingImplementsClause(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/com/example");
        Files.createDirectories(pkg);

        Path classFile = pkg.resolve("AuditService.java");
        Files.writeString(classFile,
            "package com.example;\n\n" +
            "public class AuditService implements java.io.Serializable {\n" +
            "    public void log(String event) {}\n" +
            "}\n");

        List<RefactoringChange> changes = engine.extractInterface(tempDir, classFile, "AuditService");

        assertEquals(2, changes.size());
        String modifiedClass = Files.readString(classFile);
        assertTrue(modifiedClass.contains("implements java.io.Serializable, IAuditService"));
    }

    @Test
    void testExtractInterfaceFailsWhenNoPublicMethods(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/com/example");
        Files.createDirectories(pkg);

        Path classFile = pkg.resolve("HiddenService.java");
        String originalSource =
            "package com.example;\n\n" +
            "public class HiddenService {\n" +
            "    private void secret() {}\n" +
            "}\n";
        Files.writeString(classFile, originalSource);

        IOException ex = assertThrows(IOException.class, () ->
            engine.extractInterface(tempDir, classFile, "HiddenService"));

        assertTrue(ex.getMessage().contains("No public methods found"));
        // Source file must remain completely unmodified
        assertEquals(originalSource, Files.readString(classFile));
        assertFalse(Files.exists(pkg.resolve("IHiddenService.java")));
    }

    @Test
    void testExtractInterfacePreservesImportsAndThrowsAndExcludesStatic(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/com/example");
        Files.createDirectories(pkg);

        Path classFile = pkg.resolve("ComplexService.java");
        String originalSource =
            "package com.example;\n\n" +
            "import java.util.List;\n" +
            "import java.io.IOException;\n\n" +
            "public class ComplexService {\n" +
            "    public static void globalInit() {}\n" +
            "    public List<String> fetchItems() throws IOException {\n" +
            "        return List.of(\"a\", \"b\");\n" +
            "    }\n" +
            "}\n";
        Files.writeString(classFile, originalSource);

        List<RefactoringChange> changes = engine.extractInterface(tempDir, classFile, "ComplexService");
        assertEquals(2, changes.size());

        Path interfaceFile = pkg.resolve("IComplexService.java");
        assertTrue(Files.exists(interfaceFile));
        String interfaceSource = Files.readString(interfaceFile);

        // Excludes static method
        assertFalse(interfaceSource.contains("globalInit"), "Static methods must be excluded from interface");

        // Preserves imports
        assertTrue(interfaceSource.contains("import java.util.List;"));
        assertTrue(interfaceSource.contains("import java.io.IOException;"));

        // Preserves return type and throws clause
        assertTrue(interfaceSource.contains("List<String> fetchItems() throws java.io.IOException;") ||
                   interfaceSource.contains("List<String> fetchItems() throws IOException;"));
    }

    @Test
    void testIntroduceInterfaceRetargetsClientTypesButKeepsConstructor(@TempDir Path tempDir) throws IOException {
        Path pkg = tempDir.resolve("src/com/example");
        Files.createDirectories(pkg);
        Path provider = pkg.resolve("PaymentService.java");
        Files.writeString(provider,
            "package com.example;\npublic class PaymentService {\n    public boolean charge(String id, double amount) { return true; }\n}\n");
        Path client = pkg.resolve("OrderService.java");
        Files.writeString(client,
            "package com.example;\npublic class OrderService {\n    private PaymentService paymentService = new PaymentService();\n    public void checkout(String id) { paymentService.charge(id, 1); }\n}\n");

        List<RefactoringChange> changes = engine.introduceInterfaceBetween(
            tempDir, client, provider, "OrderService", "PaymentService");
        assertFalse(changes.isEmpty());
        assertTrue(Files.exists(pkg.resolve("IPaymentService.java")));
        String rewritten = Files.readString(client);
        assertTrue(rewritten.contains("private IPaymentService paymentService"));
        assertTrue(rewritten.contains("new PaymentService()"));
        String originalProvider = Files.readString(provider);
        assertTrue(originalProvider.contains("implements IPaymentService"));
    }
}
