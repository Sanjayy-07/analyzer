package com.archanalyzer.parser;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;
import com.archanalyzer.model.ProjectModel.DependencyEdge;
import com.archanalyzer.model.ProjectModel.SourceFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modular Language Adapter for Python (.py, .pyw).
 * Extracts functions, classes, methods, imports (import, from ... import),
 * decision points, and component relationships into the shared ProjectModel.
 */
public class PythonLanguageAdapter implements LanguageAdapter {

    private static final Set<String> EXTENSIONS = Set.of(".py", ".pyw");
    private final LanguageCapability capabilities = LanguageCapability.python();

    @Override
    public String language() { return "python"; }

    @Override
    public String analysisDepth() { return capabilities.getDepthSummary(); }

    @Override
    public Set<String> supportedExtensions() { return EXTENSIONS; }

    @Override
    public LanguageCapability getCapabilities() { return capabilities; }

    @Override
    public boolean canParse(Path file) {
        if (file == null) return false;
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return EXTENSIONS.contains(name.substring(dot));
    }

    @Override
    public void parseInto(Path projectRoot, Path file, ProjectModel model) throws IOException {
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
        String source = Files.readString(file, StandardCharsets.UTF_8);

        SourceFile sourceFile = new SourceFile(relativePath, language(), analysisDepth());
        int loc = countNonBlankLines(source);
        sourceFile.setLinesOfCode(loc);

        String moduleName = toModuleName(relativePath);

        // Top-level module component node
        ComponentNode moduleNode = new ComponentNode(
            moduleName,
            getSimpleModuleName(moduleName),
            getPackageOrDir(relativePath),
            ComponentNode.Kind.MODULE,
            relativePath
        );
        moduleNode.setLoc(loc);
        inferPythonLayer(moduleNode, relativePath);
        model.addComponent(moduleNode);
        sourceFile.addComponentId(moduleName);

        // 1. Extract imports: "import x" or "from x import y"
        List<String> imports = extractImports(source);
        for (String imp : imports) {
            model.addDependency(new DependencyEdge(moduleName, imp, DependencyEdge.Type.IMPORT));
        }

        // 2. Extract classes: "class Name(Base):"
        List<PyClassInfo> classes = extractClasses(source, moduleName);
        for (PyClassInfo ci : classes) {
            ComponentNode classNode = new ComponentNode(
                ci.fqName,
                ci.name,
                moduleNode.getPackageName(),
                ComponentNode.Kind.CLASS,
                relativePath
            );
            classNode.setLoc(ci.loc);
            classNode.setMethodCount(ci.methods.size());
            classNode.setCyclomaticComplexity(ci.complexity);
            if (ci.baseClass != null && !ci.baseClass.isBlank() && !ci.baseClass.equals("object")) {
                classNode.setExtendsClass(ci.baseClass);
                model.addDependency(new DependencyEdge(ci.fqName, ci.baseClass, DependencyEdge.Type.EXTENDS));
            }
            inferPythonLayer(classNode, relativePath);
            model.addComponent(classNode);
            sourceFile.addComponentId(ci.fqName);
            model.addDependency(new DependencyEdge(moduleName, ci.fqName, DependencyEdge.Type.FIELD_TYPE));
        }

        // 3. Extract top-level functions: "def func_name(...):"
        List<PyFuncInfo> functions = extractFunctions(source, moduleName);
        for (PyFuncInfo fi : functions) {
            ComponentNode funcNode = new ComponentNode(
                fi.fqName,
                fi.name,
                moduleNode.getPackageName(),
                ComponentNode.Kind.FUNCTION,
                relativePath
            );
            funcNode.setLoc(fi.loc);
            funcNode.setCyclomaticComplexity(fi.complexity);
            funcNode.setMethodCount(1);
            inferPythonLayer(funcNode, relativePath);
            model.addComponent(funcNode);
            sourceFile.addComponentId(fi.fqName);
            model.addDependency(new DependencyEdge(moduleName, fi.fqName, DependencyEdge.Type.CALL));
        }

        moduleNode.setCyclomaticComplexity(countDecisionPoints(source));
        model.addFile(sourceFile);
    }

    private static final Pattern IMPORT_STMT = Pattern.compile(
        "(?:^|\\n)\\s*import\\s+([A-Za-z0-9_.,\\s]+)");
    private static final Pattern FROM_IMPORT_STMT = Pattern.compile(
        "(?:^|\\n)\\s*from\\s+([A-Za-z0-9_.]+)\\s+import\\s+([A-Za-z0-9_*,\\s]+)");
    private static final Pattern CLASS_DEF = Pattern.compile(
        "(?:^|\\n)\\s*class\\s+([A-Za-z0-9_]+)(?:\\s*\\(([^)]*)\\))?\\s*:");
    private static final Pattern TOP_FUNC_DEF = Pattern.compile(
        "(?:^|\\n)def\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*(?:->\\s*[^:]+)?\\s*:");

    private List<String> extractImports(String source) {
        Set<String> imports = new LinkedHashSet<>();
        Matcher m1 = IMPORT_STMT.matcher(source);
        while (m1.find()) {
            String targets = m1.group(1);
            for (String t : targets.split(",")) {
                String clean = t.trim();
                if (!clean.isEmpty()) imports.add(clean.split("\\s+as\\s+")[0].trim());
            }
        }
        Matcher m2 = FROM_IMPORT_STMT.matcher(source);
        while (m2.find()) {
            String mod = m2.group(1).trim();
            imports.add(mod);
        }
        return new ArrayList<>(imports);
    }

    private List<PyClassInfo> extractClasses(String source, String moduleName) {
        List<PyClassInfo> list = new ArrayList<>();
        Matcher m = CLASS_DEF.matcher(source);
        while (m.find()) {
            String name = m.group(1);
            String base = m.group(2) != null ? m.group(2).trim() : null;
            PyClassInfo ci = new PyClassInfo(moduleName + "." + name, name, base);
            ci.loc = 10;
            ci.complexity = 1;
            list.add(ci);
        }
        return list;
    }

    private List<PyFuncInfo> extractFunctions(String source, String moduleName) {
        List<PyFuncInfo> list = new ArrayList<>();
        Matcher m = TOP_FUNC_DEF.matcher(source);
        while (m.find()) {
            String name = m.group(1);
            if (name.startsWith("__") && name.endsWith("__")) continue;
            PyFuncInfo fi = new PyFuncInfo(moduleName + "." + name, name);
            fi.loc = 5;
            fi.complexity = 1;
            list.add(fi);
        }
        return list;
    }

    private void inferPythonLayer(ComponentNode node, String relativePath) {
        String p = relativePath.toLowerCase();
        String n = node.getSimpleName().toLowerCase();
        if (p.contains("controller") || p.contains("views") || p.contains("routes") || p.contains("api")) {
            node.addLayer("controller");
        } else if (p.contains("service") || n.contains("service")) {
            node.addLayer("service");
        } else if (p.contains("model") || p.contains("entities") || p.contains("schema")) {
            node.addLayer("model");
        } else if (p.contains("repo") || p.contains("dao") || p.contains("db")) {
            node.addLayer("repository");
        } else {
            node.addLayer("other");
        }
    }

    private String toModuleName(String relativePath) {
        String p = relativePath.replace('\\', '/');
        int dot = p.lastIndexOf('.');
        if (dot > 0) p = p.substring(0, dot);
        return p.replace('/', '.');
    }

    private String getSimpleModuleName(String moduleName) {
        int dot = moduleName.lastIndexOf('.');
        return dot >= 0 ? moduleName.substring(dot + 1) : moduleName;
    }

    private String getPackageOrDir(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash > 0 ? relativePath.substring(0, slash).replace('/', '.') : "";
    }

    private int countNonBlankLines(String source) {
        int count = 0;
        for (String line : source.split("\n", -1)) {
            if (!line.isBlank()) count++;
        }
        return count;
    }

    private int countDecisionPoints(String text) {
        int count = 0;
        String[] keywords = {"if", "elif", "for", "while", "except", "and", "or"};
        for (String kw : keywords) {
            Pattern p = Pattern.compile("\\b" + kw + "\\b");
            Matcher m = p.matcher(text);
            while (m.find()) count++;
        }
        return count;
    }

    private static class PyClassInfo {
        final String fqName, name, baseClass;
        int loc, complexity;
        final List<String> methods = new ArrayList<>();

        PyClassInfo(String fqName, String name, String baseClass) {
            this.fqName = fqName;
            this.name = name;
            this.baseClass = baseClass;
        }
    }

    private static class PyFuncInfo {
        final String fqName, name;
        int loc, complexity;

        PyFuncInfo(String fqName, String name) {
            this.fqName = fqName;
            this.name = name;
        }
    }
}
