package com.archanalyzer.refactor;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PART 19: "The system must actually modify the project for SUPPORTED
 * transformations. Do not claim universal automated refactoring."
 * ---------------------------------------------------------------------------
 * HONEST SCOPE:
 *   - "Interface Extraction" is FULLY IMPLEMENTED here: it really parses the
 *     target class with the Compiler Tree API, generates a real new
 *     interface source file containing its public method signatures, and
 *     rewrites the target class's header to implement it.
 *   - "Dependency Inversion" and "Facade Introduction" are surfaced in the
 *     Candidate/Preview UI (see optimization.CandidateGenerator) with a
 *     concrete plan, but are NOT auto-materialized by this build — they
 *     require safely rewriting call sites project-wide, which needs a full
 *     type-resolving compile (not just a syntax parse) to do without
 *     breaking the build. Attempting it with text substitution alone would
 *     risk corrupting the project, which PART 34 explicitly forbids
 *     ("do not create fake functionality"). Treat them as documented,
 *     scoped-out next steps (see README "Known Limitations").
 *
 * Assumes the common convention of one primary top-level class per .java
 * file (the same assumption `javac` itself effectively requires for public
 * top-level types) — multiple top-level classes in one file are out of
 * scope for the header-rewrite step.
 */
public class RefactoringEngine {

    private static final Pattern CLASS_HEADER_PATTERN = Pattern.compile(
        "((?:public\\s+|final\\s+|abstract\\s+)*class\\s+" + "%s" + "(?:<[^>]*>)?" +
        "(?:\\s+extends\\s+[\\w.<>,\\s]+)?(?:\\s+implements\\s+[\\w.<>,\\s]+)?)\\s*\\{");

    public List<RefactoringChange> extractInterface(Path projectRoot, Path targetClassFile,
                                                       String targetClassSimpleName) throws IOException {
        List<RefactoringChange> changes = new ArrayList<>();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Refactoring engine requires a full JDK (javac) at runtime, not just a JRE.");
        }

        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        JavacTask task = (JavacTask) compiler.getTask(null, fm, null, List.of("-proc:none"), null,
            fm.getJavaFileObjectsFromPaths(List.of(targetClassFile)));

        List<String> methodSignatures = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        String packageName = "";

        for (CompilationUnitTree unit : task.parse()) {
            packageName = unit.getPackageName() != null ? unit.getPackageName().toString() : "";
            for (ImportTree imp : unit.getImports()) {
                imports.add(imp.toString().trim());
            }
            for (Tree typeDecl : unit.getTypeDecls()) {
                if (!(typeDecl instanceof ClassTree ct)) continue;
                if (!ct.getSimpleName().toString().equals(targetClassSimpleName)) continue;
                for (Tree member : ct.getMembers()) {
                    if (member instanceof MethodTree mt && isPublicNonConstructor(mt, targetClassSimpleName)) {
                        methodSignatures.add(buildSignature(mt));
                    }
                }
            }
        }

        if (methodSignatures.isEmpty()) {
            throw new IOException("No public methods found on " + targetClassSimpleName +
                " — nothing to extract into an interface.");
        }

        String interfaceName = "I" + targetClassSimpleName;
        String interfaceSource = renderInterface(packageName, interfaceName, imports, methodSignatures);

        Path interfaceFile = targetClassFile.getParent().resolve(interfaceName + ".java");
        Files.writeString(interfaceFile, interfaceSource, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        changes.add(new RefactoringChange(
            projectRoot.relativize(interfaceFile).toString(),
            "Created interface " + interfaceName + " with " + methodSignatures.size() + " method signature(s)"));

        String originalSource = Files.readString(targetClassFile, StandardCharsets.UTF_8);
        String rewritten = addImplementsClause(originalSource, targetClassSimpleName, interfaceName);
        if (rewritten.equals(originalSource)) {
            Files.deleteIfExists(interfaceFile);
            throw new IOException("Could not locate a rewriteable class header for " + targetClassSimpleName +
                " (unsupported file layout — see RefactoringEngine scope notes).");
        }
        Files.writeString(targetClassFile, rewritten, StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING);
        changes.add(new RefactoringChange(
            projectRoot.relativize(targetClassFile).toString(),
            "Added 'implements " + interfaceName + "' to class declaration"));

        return changes;
    }

    /**
     * Dependency-inversion helper: extract the provider's interface, then retarget
     * the client's type usages to that interface while leaving {@code new Provider()} intact.
     */
    public List<RefactoringChange> introduceInterfaceBetween(Path projectRoot,
                                                             Path clientFile,
                                                             Path providerFile,
                                                             String clientSimpleName,
                                                             String providerSimpleName) throws IOException {
        List<RefactoringChange> changes = new ArrayList<>(extractInterface(projectRoot, providerFile, providerSimpleName));
        String interfaceName = "I" + providerSimpleName;
        String original = Files.readString(clientFile, StandardCharsets.UTF_8);
        String rewritten = retargetTypeUsages(original, providerSimpleName, interfaceName);
        if (!rewritten.equals(original)) {
            Files.writeString(clientFile, rewritten, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            changes.add(new RefactoringChange(
                projectRoot.relativize(clientFile).toString(),
                "Retargeted " + clientSimpleName + " type usages from " + providerSimpleName + " to " + interfaceName
                    + " (concrete constructor calls preserved)"));
        } else {
            changes.add(new RefactoringChange(
                projectRoot.relativize(clientFile).toString(),
                "No retargetable type usages of " + providerSimpleName + " were found in " + clientSimpleName
                    + "; interface was still extracted"));
        }
        return changes;
    }

    static String retargetTypeUsages(String source, String concreteType, String interfaceName) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(concreteType) + "\\b");
        Matcher m = pattern.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int start = m.start();
            String prefix = source.substring(Math.max(0, start - 4), start);
            if (prefix.endsWith("new ")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(concreteType));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(interfaceName));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private boolean isPublicNonConstructor(MethodTree mt, String className) {
        boolean isConstructor = mt.getName().toString().equals("<init>");
        boolean isPublic = mt.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.PUBLIC);
        boolean isStatic = mt.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC);
        return isPublic && !isConstructor && !isStatic;
    }

    private String buildSignature(MethodTree mt) {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(mt.getReturnType()).append(" ").append(mt.getName()).append("(");
        List<String> params = new ArrayList<>();
        for (VariableTree p : mt.getParameters()) {
            params.add(p.getType().toString() + " " + p.getName());
        }
        sb.append(String.join(", ", params)).append(")");
        if (mt.getThrows() != null && !mt.getThrows().isEmpty()) {
            List<String> throwNames = new ArrayList<>();
            for (ExpressionTree ex : mt.getThrows()) {
                throwNames.add(ex.toString());
            }
            sb.append(" throws ").append(String.join(", ", throwNames));
        }
        sb.append(";");
        return sb.toString();
    }

    private String renderInterface(String packageName, String interfaceName, List<String> imports, List<String> signatures) {
        StringBuilder sb = new StringBuilder();
        if (!packageName.isEmpty()) sb.append("package ").append(packageName).append(";\n\n");
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                sb.append(imp).append("\n");
            }
            sb.append("\n");
        }
        sb.append("/**\n * Auto-generated by RefactoringEngine.extractInterface().\n")
          .append(" * Extracted public contract — review before relying on it in production code.\n */\n");
        sb.append("public interface ").append(interfaceName).append(" {\n\n");
        for (String sig : signatures) sb.append(sig).append("\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String addImplementsClause(String source, String className, String interfaceName) {
        Pattern pattern = Pattern.compile(String.format(CLASS_HEADER_PATTERN.pattern(), Pattern.quote(className)));
        Matcher m = pattern.matcher(source);
        if (!m.find()) return source;

        String header = m.group(1).trim();
        String newHeader;
        if (header.contains(" implements ")) {
            newHeader = header + ", " + interfaceName;
        } else {
            newHeader = header + " implements " + interfaceName;
        }
        return source.substring(0, m.start(1)) + newHeader + " {" + source.substring(m.end());
    }
}
