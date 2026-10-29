package com.archanalyzer.model;

import java.util.*;

/**
 * COMMON PROJECT MODEL
 * ---------------------------------------------------------------------------
 * Language-neutral representation that every ParserAdapter (Java, Python,
 * JS/TS, ...) must populate. All downstream engines (graph, metrics, smell
 * detection, ML, optimization, refactoring) operate ONLY on this model, never
 * on language-specific ASTs directly. This is what makes the analyzer
 * extensible to new languages without touching the rest of the pipeline.
 *
 * Advanced Java syllabus mapping:
 *  - Module 1 (Generics, Collections, Streams) is used throughout this class
 *    and its consumers (see graph/metrics/ml packages).
 */
public class ProjectModel {

    private final String projectName;
    private final Map<String, SourceFile> filesByPath = new LinkedHashMap<>();
    private final Map<String, ComponentNode> componentsById = new LinkedHashMap<>();
    private final List<DependencyEdge> dependencies = new ArrayList<>();
    private final Set<String> detectedLanguages = new LinkedHashSet<>();
    private final Map<String, String> buildInfo = new LinkedHashMap<>(); // e.g. "buildTool" -> "maven"
    private final List<String> excludedNonJavaFiles = new ArrayList<>();
    private final Set<String> excludedLanguages = new LinkedHashSet<>();
    private final Map<String, String> parseErrors = new LinkedHashMap<>();
    private final Map<String, com.archanalyzer.parser.LanguageCapability> languageCapabilities = new LinkedHashMap<>();

    public ProjectModel(String projectName) {
        this.projectName = projectName;
    }

    public void recordLanguageCapability(com.archanalyzer.parser.LanguageCapability cap) {
        if (cap != null) {
            languageCapabilities.put(cap.getLanguage().toLowerCase(), cap);
        }
    }

    public Map<String, com.archanalyzer.parser.LanguageCapability> getLanguageCapabilities() {
        return Collections.unmodifiableMap(languageCapabilities);
    }

    public String getProjectName() { return projectName; }

    public void addFile(SourceFile file) {
        filesByPath.put(file.getRelativePath(), file);
        detectedLanguages.add(file.getLanguage());
    }

    public void addComponent(ComponentNode component) {
        componentsById.put(component.getId(), component);
    }

    public void addDependency(DependencyEdge edge) {
        dependencies.add(edge);
    }

    public void addExcludedNonJavaFile(String relativePath) {
        excludedNonJavaFiles.add(relativePath);
    }

    public void addExcludedLanguage(String language) {
        if (language != null && !language.isBlank()) {
            excludedLanguages.add(language);
        }
    }

    public List<String> getExcludedNonJavaFiles() {
        return Collections.unmodifiableList(excludedNonJavaFiles);
    }

    public Set<String> getExcludedLanguages() {
        return Collections.unmodifiableSet(excludedLanguages);
    }

    public boolean isMixedLanguage() {
        return !excludedNonJavaFiles.isEmpty();
    }

    public void addParseError(String relativePath, String error) {
        parseErrors.put(relativePath, error);
    }

    public Map<String, String> getParseErrors() {
        return Collections.unmodifiableMap(parseErrors);
    }

    public boolean hasParseErrors() {
        return !parseErrors.isEmpty();
    }

    public Collection<SourceFile> getFiles() { return filesByPath.values(); }
    public Optional<SourceFile> getFile(String relativePath) {
        return Optional.ofNullable(filesByPath.get(relativePath));
    }

    public Collection<ComponentNode> getComponents() { return componentsById.values(); }
    public Optional<ComponentNode> getComponent(String id) {
        return Optional.ofNullable(componentsById.get(id));
    }

    public List<DependencyEdge> getDependencies() { return Collections.unmodifiableList(dependencies); }

    public Set<String> getDetectedLanguages() { return detectedLanguages; }

    public void putBuildInfo(String key, String value) { buildInfo.put(key, value); }
    public Map<String, String> getBuildInfo() { return buildInfo; }

    public int fileCount() { return filesByPath.size(); }
    public int componentCount() { return componentsById.size(); }

    // -------------------------------------------------------------------
    // Nested model types
    // -------------------------------------------------------------------

    /** A single discovered source file. */
    public static class SourceFile {
        private final String relativePath;
        private final String language;      // "java", "python", "javascript", "typescript", ...
        private final String analysisDepth; // "Full" or "Partial"
        private int linesOfCode;
        private final List<String> componentIds = new ArrayList<>();

        public SourceFile(String relativePath, String language, String analysisDepth) {
            this.relativePath = relativePath;
            this.language = language;
            this.analysisDepth = analysisDepth;
        }

        public String getRelativePath() { return relativePath; }
        public String getLanguage() { return language; }
        public String getAnalysisDepth() { return analysisDepth; }
        public int getLinesOfCode() { return linesOfCode; }
        public void setLinesOfCode(int loc) { this.linesOfCode = loc; }
        public void addComponentId(String id) { componentIds.add(id); }
        public List<String> getComponentIds() { return componentIds; }
    }

    /** A class, interface, module, or function-level unit — the graph's vertex type. */
    public static class ComponentNode {
        public enum Kind { CLASS, INTERFACE, ENUM, RECORD, MODULE, FUNCTION, PACKAGE }

        private final String id;              // fully-qualified name, unique within project
        private final String simpleName;
        private final String packageName;
        private final Kind kind;
        private final String declaredInFile;
        private final Set<String> layer = new LinkedHashSet<>(); // e.g. "controller", "service", "repository"
        private int loc;
        private int cyclomaticComplexity;
        private int methodCount;
        private int fieldCount;
        private final List<String> implementsInterfaces = new ArrayList<>();
        private String extendsClass;

        public ComponentNode(String id, String simpleName, String packageName, Kind kind, String declaredInFile) {
            this.id = id;
            this.simpleName = simpleName;
            this.packageName = packageName;
            this.kind = kind;
            this.declaredInFile = declaredInFile;
        }

        public String getId() { return id; }
        public String getSimpleName() { return simpleName; }
        public String getPackageName() { return packageName; }
        public Kind getKind() { return kind; }
        public String getDeclaredInFile() { return declaredInFile; }
        public Set<String> getLayer() { return layer; }
        public void addLayer(String l) { layer.add(l); }
        public int getLoc() { return loc; }
        public void setLoc(int loc) { this.loc = loc; }
        public int getCyclomaticComplexity() { return cyclomaticComplexity; }
        public void setCyclomaticComplexity(int c) { this.cyclomaticComplexity = c; }
        public int getMethodCount() { return methodCount; }
        public void setMethodCount(int m) { this.methodCount = m; }
        public int getFieldCount() { return fieldCount; }
        public void setFieldCount(int f) { this.fieldCount = f; }
        public List<String> getImplementsInterfaces() { return implementsInterfaces; }
        public String getExtendsClass() { return extendsClass; }
        public void setExtendsClass(String e) { this.extendsClass = e; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ComponentNode)) return false;
            return id.equals(((ComponentNode) o).id);
        }
        @Override
        public int hashCode() { return id.hashCode(); }
    }

    /** A directed dependency relationship — the graph's edge type. */
    public static class DependencyEdge {
        public enum Type { IMPORT, EXTENDS, IMPLEMENTS, FIELD_TYPE, METHOD_PARAM, METHOD_RETURN, INSTANTIATION, CALL }

        private final String fromComponentId;
        private final String toComponentId;
        private final Type type;

        public DependencyEdge(String fromComponentId, String toComponentId, Type type) {
            this.fromComponentId = fromComponentId;
            this.toComponentId = toComponentId;
            this.type = type;
        }

        public String getFrom() { return fromComponentId; }
        public String getTo() { return toComponentId; }
        public Type getType() { return type; }

        @Override
        public String toString() {
            return fromComponentId + " --[" + type + "]--> " + toComponentId;
        }
    }
}
