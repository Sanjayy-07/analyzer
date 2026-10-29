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
 * Modular Language Adapter for JavaScript (.js, .jsx, .mjs, .cjs).
 * Performs syntax parsing, component extraction (classes, functions, React components,
 * Express API routes), dependency extraction (ES6 imports, CommonJS require),
 * and complexity analysis into the shared ProjectModel.
 */
public class JavaScriptLanguageAdapter implements LanguageAdapter {

    private static final Set<String> EXTENSIONS = Set.of(".js", ".jsx", ".mjs", ".cjs");
    private final LanguageCapability capabilities = LanguageCapability.javaScript();

    @Override
    public String language() { return "javascript"; }

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

        // Remove comments and string literals for reliable token extraction
        String cleanSource = stripCommentsAndStrings(source);

        // Discover module identifier (e.g., "frontend/src/App" or "services/paymentService")
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
        inferJsLayer(moduleNode, relativePath, source);
        model.addComponent(moduleNode);
        sourceFile.addComponentId(moduleName);

        // 1. Extract imports (ES6 and CommonJS)
        List<String> importedPaths = extractImports(cleanSource);
        for (String imported : importedPaths) {
            String resolvedDep = resolveImportToComponent(relativePath, imported);
            model.addDependency(new DependencyEdge(moduleName, resolvedDep, DependencyEdge.Type.IMPORT));
        }

        // 2. Extract classes
        List<JsClassInfo> classes = extractClasses(cleanSource, source, moduleName, relativePath);
        for (JsClassInfo ci : classes) {
            ComponentNode classNode = new ComponentNode(
                ci.fqName,
                ci.simpleName,
                moduleNode.getPackageName(),
                ComponentNode.Kind.CLASS,
                relativePath
            );
            classNode.setLoc(ci.loc);
            classNode.setMethodCount(ci.methods.size());
            classNode.setFieldCount(ci.fields.size());
            classNode.setCyclomaticComplexity(ci.complexity);
            if (ci.superClass != null) {
                classNode.setExtendsClass(ci.superClass);
                String resolvedSuper = resolveImportToComponent(relativePath, ci.superClass);
                model.addDependency(new DependencyEdge(ci.fqName, resolvedSuper, DependencyEdge.Type.EXTENDS));
            }
            inferJsLayer(classNode, relativePath, source);
            model.addComponent(classNode);
            sourceFile.addComponentId(ci.fqName);
            // Class depends on enclosing module
            model.addDependency(new DependencyEdge(moduleName, ci.fqName, DependencyEdge.Type.FIELD_TYPE));
        }

        // 3. Extract functions / React components
        List<JsFunctionInfo> functions = extractFunctions(cleanSource, source, moduleName, relativePath);
        for (JsFunctionInfo fi : functions) {
            ComponentNode funcNode = new ComponentNode(
                fi.fqName,
                fi.name,
                moduleNode.getPackageName(),
                fi.isReactComponent ? ComponentNode.Kind.CLASS : ComponentNode.Kind.FUNCTION,
                relativePath
            );
            funcNode.setLoc(fi.loc);
            funcNode.setCyclomaticComplexity(fi.complexity);
            funcNode.setMethodCount(1);
            if (fi.isReactComponent) {
                funcNode.addLayer("ui");
            } else {
                inferJsLayer(funcNode, relativePath, source);
            }
            model.addComponent(funcNode);
            sourceFile.addComponentId(fi.fqName);
            model.addDependency(new DependencyEdge(moduleName, fi.fqName, DependencyEdge.Type.CALL));
        }

        // 4. Extract Express / API routes (e.g. app.get('/api/orders', ...))
        List<String> routes = extractRoutes(cleanSource);
        for (String route : routes) {
            String routeId = moduleName + ":" + route;
            ComponentNode routeNode = new ComponentNode(
                routeId,
                route,
                moduleNode.getPackageName(),
                ComponentNode.Kind.FUNCTION,
                relativePath
            );
            routeNode.addLayer("controller");
            routeNode.setLoc(5);
            model.addComponent(routeNode);
            sourceFile.addComponentId(routeId);
            model.addDependency(new DependencyEdge(moduleName, routeId, DependencyEdge.Type.CALL));
        }

        // 5. Extract API calls (fetch('/api/payment'), axios.post('/api/charge'))
        List<String> apiCalls = extractApiCalls(source);
        for (String endpoint : apiCalls) {
            String targetEndpointId = "API:" + endpoint;
            model.addDependency(new DependencyEdge(moduleName, targetEndpointId, DependencyEdge.Type.CALL));
        }

        // Compute overall module cyclomatic complexity
        int totalComplexity = countDecisionPoints(cleanSource);
        moduleNode.setCyclomaticComplexity(totalComplexity);

        model.addFile(sourceFile);
    }

    private static final Pattern IMPORT_ES6 = Pattern.compile(
        "(?:^|\\n)\\s*import\\s+(?:[\\w*\\s{},$]+from\\s+)?['\"]([^'\"]+)['\"]", Pattern.MULTILINE);
    private static final Pattern REQUIRE_CJS = Pattern.compile(
        "require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern CLASS_DEF = Pattern.compile(
        "(?:^|\\n)\\s*(?:export\\s+)?(?:default\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+extends\\s+([A-Za-z_$][A-Za-z0-9_$.]*))?\\s*\\{");
    private static final Pattern FUNCTION_DEF = Pattern.compile(
        "(?:^|\\n)\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)");
    private static final Pattern ARROW_FUNC_DEF = Pattern.compile(
        "(?:^|\\n)\\s*(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:async\\s*)?(?:\\(([^)]*)\\)|[A-Za-z_$][A-Za-z0-9_$]*)\\s*=>");
    private static final Pattern ROUTE_DEF = Pattern.compile(
        "(?:app|router)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern API_CALL = Pattern.compile(
        "(?:fetch|axios\\.(?:get|post|put|delete|patch))\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    private List<String> extractImports(String cleanSource) {
        Set<String> imports = new LinkedHashSet<>();
        Matcher m1 = IMPORT_ES6.matcher(cleanSource);
        while (m1.find()) {
            imports.add(m1.group(1));
        }
        Matcher m2 = REQUIRE_CJS.matcher(cleanSource);
        while (m2.find()) {
            imports.add(m2.group(1));
        }
        return new ArrayList<>(imports);
    }

    private List<JsClassInfo> extractClasses(String cleanSource, String rawSource, String moduleName, String relativePath) {
        List<JsClassInfo> list = new ArrayList<>();
        Matcher m = CLASS_DEF.matcher(cleanSource);
        while (m.find()) {
            String className = m.group(1);
            String superClass = m.group(2);
            String fqName = moduleName + "." + className;
            JsClassInfo ci = new JsClassInfo(fqName, className, superClass);
            ci.loc = Math.max(1, countClassLoc(rawSource, className));
            ci.complexity = 1 + countDecisionPoints(extractMethodBodies(cleanSource, m.end()));
            list.add(ci);
        }
        return list;
    }

    private List<JsFunctionInfo> extractFunctions(String cleanSource, String rawSource, String moduleName, String relativePath) {
        List<JsFunctionInfo> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Traditional functions
        Matcher m1 = FUNCTION_DEF.matcher(cleanSource);
        while (m1.find()) {
            String name = m1.group(1);
            if (name == null || name.isBlank() || seen.contains(name)) continue;
            seen.add(name);
            boolean isReact = Character.isUpperCase(name.charAt(0)) && (rawSource.contains("return <") || rawSource.contains("return ( <") || rawSource.contains("React.createElement"));
            JsFunctionInfo fi = new JsFunctionInfo(moduleName + "." + name, name, isReact);
            fi.loc = 5;
            fi.complexity = 1 + countDecisionPoints(name);
            list.add(fi);
        }

        // Arrow functions assigned to const/let
        Matcher m2 = ARROW_FUNC_DEF.matcher(cleanSource);
        while (m2.find()) {
            String name = m2.group(1);
            if (name == null || name.isBlank() || seen.contains(name)) continue;
            seen.add(name);
            boolean isReact = Character.isUpperCase(name.charAt(0)) && (rawSource.contains("return <") || rawSource.contains("return ( <") || rawSource.contains("=> <") || rawSource.contains("=> ( <"));
            JsFunctionInfo fi = new JsFunctionInfo(moduleName + "." + name, name, isReact);
            fi.loc = 5;
            fi.complexity = 1;
            list.add(fi);
        }
        return list;
    }

    private List<String> extractRoutes(String cleanSource) {
        List<String> routes = new ArrayList<>();
        Matcher m = ROUTE_DEF.matcher(cleanSource);
        while (m.find()) {
            String method = m.group(1).toUpperCase();
            String path = m.group(2);
            routes.add(method + " " + path);
        }
        return routes;
    }

    private List<String> extractApiCalls(String rawSource) {
        List<String> endpoints = new ArrayList<>();
        Matcher m = API_CALL.matcher(rawSource);
        while (m.find()) {
            endpoints.add(m.group(1));
        }
        return endpoints;
    }

    private void inferJsLayer(ComponentNode node, String relativePath, String source) {
        String p = relativePath.toLowerCase();
        String n = node.getSimpleName().toLowerCase();
        if (p.contains("controller") || n.contains("controller") || p.contains("routes") || p.contains("api/")) {
            node.addLayer("controller");
        } else if (p.contains("service") || n.contains("service")) {
            node.addLayer("service");
        } else if (p.contains("model") || p.contains("entity") || p.contains("schemas")) {
            node.addLayer("model");
        } else if (p.contains("repository") || p.contains("dao") || p.contains("db/")) {
            node.addLayer("repository");
        } else if (p.contains("component") || p.contains("view") || p.contains("page") || source.contains("React") || source.contains("render(")) {
            node.addLayer("ui");
        } else {
            node.addLayer("other");
        }
    }

    private String toModuleName(String relativePath) {
        String p = relativePath.replace('\\', '/');
        int dot = p.lastIndexOf('.');
        if (dot > 0) p = p.substring(0, dot);
        return p;
    }

    private String getSimpleModuleName(String moduleName) {
        int slash = moduleName.lastIndexOf('/');
        return slash >= 0 ? moduleName.substring(slash + 1) : moduleName;
    }

    private String getPackageOrDir(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash > 0 ? relativePath.substring(0, slash).replace('/', '.') : "";
    }

    private String resolveImportToComponent(String currentFile, String importPath) {
        if (!importPath.startsWith(".")) {
            // Third-party package, e.g. "express", "axios", "react"
            return "npm:" + importPath;
        }
        // Relative local import, e.g. "./services/payment" or "../utils/logger"
        Path parent = Path.of(currentFile).getParent();
        Path resolved = parent != null ? parent.resolve(importPath).normalize() : Path.of(importPath).normalize();
        String s = resolved.toString().replace('\\', '/');
        int dot = s.lastIndexOf('.');
        if (dot > 0 && (s.endsWith(".js") || s.endsWith(".jsx") || s.endsWith(".ts") || s.endsWith(".tsx"))) {
            s = s.substring(0, dot);
        }
        return s;
    }

    private int countNonBlankLines(String source) {
        int count = 0;
        for (String line : source.split("\n", -1)) {
            if (!line.isBlank()) count++;
        }
        return count;
    }

    private int countClassLoc(String source, String className) {
        int idx = source.indexOf("class " + className);
        if (idx < 0) return 10;
        int openBrace = source.indexOf('{', idx);
        if (openBrace < 0) return 10;
        int depth = 1;
        int lines = 1;
        for (int i = openBrace + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') lines++;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
        }
        return lines;
    }

    private String extractMethodBodies(String source, int startIdx) {
        int depth = 1;
        int end = startIdx;
        for (int i = startIdx; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }
        return end > startIdx ? source.substring(startIdx, end) : "";
    }

    private int countDecisionPoints(String text) {
        int count = 0;
        String[] keywords = {"if", "else if", "for", "while", "case", "catch", "&&", "||", "\\?"};
        for (String kw : keywords) {
            Pattern p = Pattern.compile("\\b" + kw + "\\b|(" + kw + ")");
            Matcher m = p.matcher(text);
            while (m.find()) count++;
        }
        return count;
    }

    private String stripCommentsAndStrings(String code) {
        StringBuilder sb = new StringBuilder();
        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    sb.append(c);
                }
            } else if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                } else if (c == '\n') {
                    sb.append(c);
                }
            } else if (inString) {
                sb.append(c);
                if (c == '\\') {
                    if (i + 1 < code.length()) {
                        sb.append(code.charAt(i + 1));
                        i++;
                    }
                } else if (c == stringChar) {
                    inString = false;
                }
            } else {
                if (c == '/' && next == '/') {
                    inLineComment = true;
                    i++;
                } else if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                } else if (c == '\'' || c == '"' || c == '`') {
                    inString = true;
                    stringChar = c;
                    sb.append(c);
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static class JsClassInfo {
        final String fqName, simpleName, superClass;
        int loc, complexity;
        final List<String> methods = new ArrayList<>();
        final List<String> fields = new ArrayList<>();

        JsClassInfo(String fqName, String simpleName, String superClass) {
            this.fqName = fqName;
            this.simpleName = simpleName;
            this.superClass = superClass;
        }
    }

    private static class JsFunctionInfo {
        final String fqName, name;
        final boolean isReactComponent;
        int loc, complexity;

        JsFunctionInfo(String fqName, String name, boolean isReactComponent) {
            this.fqName = fqName;
            this.name = name;
            this.isReactComponent = isReactComponent;
        }
    }
}
