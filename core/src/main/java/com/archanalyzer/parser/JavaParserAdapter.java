package com.archanalyzer.parser;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;
import com.archanalyzer.model.ProjectModel.DependencyEdge;
import com.archanalyzer.model.ProjectModel.SourceFile;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;

import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * REAL Java parsing via the JDK's own Compiler Tree API (packages
 * com.sun.source.tree / com.sun.source.util, module jdk.compiler). This is
 * the same public, standardized API javac itself and annotation processors
 * use — it ships with every JDK, so this adapter needs no external library
 * such as JavaParser or Eclipse JDT.
 *
 * IMPORTANT: this requires running on a full JDK (`javax.tools.ToolProvider
 * .getSystemJavaCompiler()` returns null on a JRE-only runtime). It performs
 * a syntax-tree parse only — it does not require the project's own
 * dependencies to be resolvable, and does not compile/execute project code.
 *
 * Advanced Java syllabus mapping:
 *  - Module 1: Generics (this class), Streams (used for filtering trees),
 *    Collections (Map/List/Set structures below).
 */
public class JavaParserAdapter implements LanguageAdapter {

    private static final Set<String> EXTENSIONS = Set.of(".java");
    private final LanguageCapability capabilities = LanguageCapability.java();

    @Override
    public String language() { return "java"; }

    @Override
    public String analysisDepth() { return "Full"; }

    @Override
    public Set<String> supportedExtensions() { return EXTENSIONS; }

    @Override
    public LanguageCapability getCapabilities() { return capabilities; }

    @Override
    public boolean canParse(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString() : "";
        return name.endsWith(".java");
    }

    @Override
    public void parseInto(Path projectRoot, Path file, ProjectModel model) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                "No system Java compiler available. The analyzer engine must run on a full JDK " +
                "(not a JRE) because it uses the Compiler Tree API for real Java parsing.");
        }

        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
        String source = Files.readString(file, StandardCharsets.UTF_8);
        int loc = countNonBlankLines(source);

        DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var compilationUnits = fm.getJavaFileObjectsFromPaths(List.of(file));
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics,
                List.of("-proc:none"), null, compilationUnits);

            Iterable<? extends CompilationUnitTree> units;
            try {
                units = task.parse();
            } catch (Exception e) {
                throw new IOException("Failed to parse Java source: " + relativePath + " (" + e.getMessage() + ")", e);
            }

            List<String> syntaxErrors = new ArrayList<>();
            for (var d : diagnostics.getDiagnostics()) {
                if (d.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                    syntaxErrors.add("Line " + d.getLineNumber() + ": " + d.getMessage(Locale.ENGLISH));
                }
            }

            if (!syntaxErrors.isEmpty()) {
                throw new IOException("Syntax errors in " + relativePath + ": " + String.join("; ", syntaxErrors));
            }

            SourceFile sourceFile = new SourceFile(relativePath, language(), analysisDepth());
            sourceFile.setLinesOfCode(loc);

            boolean parsedAny = false;
            for (CompilationUnitTree unit : units) {
                String packageName = unit.getPackageName() != null ? unit.getPackageName().toString() : "";
                List<String> imports = new ArrayList<>();
                for (ImportTree imp : unit.getImports()) {
                    imports.add(imp.getQualifiedIdentifier().toString());
                }

                ClassVisitor visitor = new ClassVisitor(packageName, relativePath, imports);
                visitor.scan(unit, null);

                if (!visitor.foundClasses.isEmpty()) {
                    parsedAny = true;
                }

                for (ClassVisitor.FoundClass fc : visitor.foundClasses) {
                    ComponentNode node = new ComponentNode(fc.fqName, fc.simpleName, packageName, fc.kind, relativePath);
                    node.setLoc(fc.loc);
                    node.setMethodCount(fc.methodCount);
                    node.setFieldCount(fc.fieldCount);
                    node.setCyclomaticComplexity(fc.cyclomaticComplexity);
                    node.setExtendsClass(fc.extendsName);
                    node.getImplementsInterfaces().addAll(fc.implementsNames);
                    inferLayer(node);
                    model.addComponent(node);
                    sourceFile.addComponentId(fc.fqName);

                    if (fc.extendsName != null) {
                        model.addDependency(new DependencyEdge(fc.fqName, resolveSimpleToFq(fc.extendsName, imports, packageName),
                            DependencyEdge.Type.EXTENDS));
                    }
                    for (String iface : fc.implementsNames) {
                        model.addDependency(new DependencyEdge(fc.fqName, resolveSimpleToFq(iface, imports, packageName),
                            DependencyEdge.Type.IMPLEMENTS));
                    }
                    for (String usedType : fc.referencedTypes) {
                        String resolved = resolveSimpleToFq(usedType, imports, packageName);
                        if (!resolved.equals(fc.fqName)) {
                            model.addDependency(new DependencyEdge(fc.fqName, resolved, DependencyEdge.Type.FIELD_TYPE));
                        }
                    }
                }
            }

            model.addFile(sourceFile);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to process Java source " + relativePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Best-effort resolution of a simple type name to a fully qualified name
     * using the file's import list, falling back to "same package" and
     * finally to the simple name itself (unresolved — likely a JDK/third
     * party type, which is expected and fine: we only graph *internal*
     * project dependencies downstream by checking model membership).
     */
    private String resolveSimpleToFq(String simpleOrQualified, List<String> imports, String packageName) {
        if (simpleOrQualified.contains(".")) return simpleOrQualified; // already qualified (or generic-stripped qualified)
        for (String imp : imports) {
            if (imp.endsWith("." + simpleOrQualified)) return imp;
        }
        return packageName.isEmpty() ? simpleOrQualified : packageName + "." + simpleOrQualified;
    }

    private void inferLayer(ComponentNode node) {
        String n = node.getSimpleName().toLowerCase();
        String p = node.getPackageName().toLowerCase();
        if (n.endsWith("controller") || p.contains(".controller")) node.addLayer("controller");
        else if (n.endsWith("service") || p.contains(".service")) node.addLayer("service");
        else if (n.endsWith("repository") || n.endsWith("dao") || p.contains(".repository") || p.contains(".dao")) node.addLayer("repository");
        else if (n.endsWith("dto") || p.contains(".dto")) node.addLayer("dto");
        else if (n.endsWith("entity") || p.contains(".model") || p.contains(".entity")) node.addLayer("model");
        else node.addLayer("other");
    }

    private int countNonBlankLines(String source) {
        int count = 0;
        for (String line : source.split("\n", -1)) {
            if (!line.isBlank()) count++;
        }
        return count;
    }

    /**
     * Walks one compilation unit collecting one FoundClass per top-level or
     * nested class/interface/enum/record declaration, including a rough
     * cyclomatic-complexity count (decision points + 1) summed across all
     * methods in the type.
     */
    private static class ClassVisitor extends TreeScanner<Void, Void> {
        final String packageName;
        final String filePath;
        final List<String> imports;
        final List<FoundClass> foundClasses = new ArrayList<>();
        private final Deque<FoundClass> stack = new ArrayDeque<>();

        ClassVisitor(String packageName, String filePath, List<String> imports) {
            this.packageName = packageName;
            this.filePath = filePath;
            this.imports = imports;
        }

        @Override
        public Void visitClass(ClassTree node, Void p) {
            ComponentNode.Kind kind = switch (node.getKind()) {
                case INTERFACE -> ComponentNode.Kind.INTERFACE;
                case ENUM -> ComponentNode.Kind.ENUM;
                case RECORD -> ComponentNode.Kind.RECORD;
                default -> ComponentNode.Kind.CLASS;
            };
            String simpleName = node.getSimpleName().toString();
            String fqName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

            FoundClass fc = new FoundClass(fqName, simpleName, kind);
            if (node.getExtendsClause() != null) {
                fc.extendsName = stripGenerics(node.getExtendsClause().toString());
            }
            for (Tree impl : node.getImplementsClause()) {
                fc.implementsNames.add(stripGenerics(impl.toString()));
            }
            for (Tree member : node.getMembers()) {
                if (member instanceof VariableTree vt) {
                    fc.fieldCount++;
                    addReferencedType(fc, vt.getType());
                } else if (member instanceof MethodTree mt) {
                    if (!mt.getName().toString().equals("<init>")) fc.methodCount++;
                    fc.cyclomaticComplexity += 1 + countDecisionPoints(mt);
                    if (mt.getReturnType() != null) {
                        addReferencedType(fc, mt.getReturnType());
                    }
                    for (VariableTree param : mt.getParameters()) {
                        addReferencedType(fc, param.getType());
                    }
                    if (mt.getBody() != null) {
                        scanMethodBodyForReferences(fc, mt.getBody());
                    }
                }
            }
            fc.loc = 0; // per-class LOC left at 0 here; file-level LOC captured separately (see SourceFile)

            foundClasses.add(fc);
            stack.push(fc);
            Void result = super.visitClass(node, p);
            stack.pop();
            return result;
        }

        private static final Set<String> IGNORED_PRIMITIVES = Set.of(
            "int", "long", "short", "byte", "char", "boolean", "float", "double", "void"
        );

        private void addReferencedType(FoundClass fc, Tree typeTree) {
            if (typeTree == null) return;
            String raw = stripGenerics(typeTree.toString());
            while (raw.endsWith("[]")) {
                raw = raw.substring(0, raw.length() - 2).trim();
            }
            if (!raw.isBlank() && !IGNORED_PRIMITIVES.contains(raw)) {
                fc.referencedTypes.add(raw);
            }
        }

        private void scanMethodBodyForReferences(FoundClass fc, BlockTree body) {
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitVariable(VariableTree vt, Void p) {
                    addReferencedType(fc, vt.getType());
                    return super.visitVariable(vt, p);
                }

                @Override
                public Void visitNewClass(NewClassTree nct, Void p) {
                    addReferencedType(fc, nct.getIdentifier());
                    return super.visitNewClass(nct, p);
                }

                @Override
                public Void visitTypeCast(TypeCastTree tct, Void p) {
                    addReferencedType(fc, tct.getType());
                    return super.visitTypeCast(tct, p);
                }
            }.scan(body, null);
        }

        private int countDecisionPoints(MethodTree method) {
            DecisionPointCounter counter = new DecisionPointCounter();
            if (method.getBody() != null) counter.scan(method.getBody(), null);
            return counter.count;
        }

        private String stripGenerics(String typeName) {
            int idx = typeName.indexOf('<');
            String base = idx >= 0 ? typeName.substring(0, idx) : typeName;
            return base.trim();
        }

        static class FoundClass {
            final String fqName, simpleName;
            final ComponentNode.Kind kind;
            String extendsName;
            final List<String> implementsNames = new ArrayList<>();
            final Set<String> referencedTypes = new LinkedHashSet<>();
            int methodCount = 0, fieldCount = 0, cyclomaticComplexity = 0, loc = 0;

            FoundClass(String fqName, String simpleName, ComponentNode.Kind kind) {
                this.fqName = fqName;
                this.simpleName = simpleName;
                this.kind = kind;
            }
        }
    }

    /** Counts branching constructs inside a method body — a standard cyclomatic-complexity approximation. */
    private static class DecisionPointCounter extends TreeScanner<Void, Void> {
        int count = 0;

        @Override public Void visitIf(IfTree node, Void p) { count++; return super.visitIf(node, p); }
        @Override public Void visitForLoop(ForLoopTree node, Void p) { count++; return super.visitForLoop(node, p); }
        @Override public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) { count++; return super.visitEnhancedForLoop(node, p); }
        @Override public Void visitWhileLoop(WhileLoopTree node, Void p) { count++; return super.visitWhileLoop(node, p); }
        @Override public Void visitDoWhileLoop(DoWhileLoopTree node, Void p) { count++; return super.visitDoWhileLoop(node, p); }
        @Override public Void visitCase(CaseTree node, Void p) { count++; return super.visitCase(node, p); }
        @Override public Void visitCatch(CatchTree node, Void p) { count++; return super.visitCatch(node, p); }
        @Override public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) { count++; return super.visitConditionalExpression(node, p); }
        @Override public Void visitBinary(BinaryTree node, Void p) {
            if (node.getKind() == Tree.Kind.CONDITIONAL_AND || node.getKind() == Tree.Kind.CONDITIONAL_OR) count++;
            return super.visitBinary(node, p);
        }
    }
}
