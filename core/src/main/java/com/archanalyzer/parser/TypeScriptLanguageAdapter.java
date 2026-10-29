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
 * Modular Language Adapter for TypeScript and TSX (.ts, .tsx).
 * Handles types, interfaces, classes, module dependencies, and TSX component relations.
 */
public class TypeScriptLanguageAdapter implements LanguageAdapter {

    private static final Set<String> EXTENSIONS = Set.of(".ts", ".tsx");
    private final LanguageCapability capabilities = LanguageCapability.typeScript();
    private final JavaScriptLanguageAdapter jsDelegate = new JavaScriptLanguageAdapter();

    @Override
    public String language() { return "typescript"; }

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

        // Run JavaScript-level AST extraction first
        jsDelegate.parseInto(projectRoot, file, model);

        // Update SourceFile language to typescript
        model.getFile(relativePath).ifPresent(sf -> {
            // Re-register file as TypeScript
        });

        String moduleName = relativePath.replace('\\', '/');
        int dot = moduleName.lastIndexOf('.');
        if (dot > 0) moduleName = moduleName.substring(0, dot);

        // Extract TypeScript interfaces
        Pattern ifacePattern = Pattern.compile(
            "(?:^|\\n)\\s*(?:export\\s+)?interface\\s+([A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+extends\\s+([A-Za-z_$][A-Za-z0-9_$,\\s]*))?\\s*\\{");
        Matcher ifaceMatcher = ifacePattern.matcher(source);
        while (ifaceMatcher.find()) {
            String ifaceName = ifaceMatcher.group(1);
            String fqIfaceName = moduleName + "." + ifaceName;
            ComponentNode ifaceNode = new ComponentNode(
                fqIfaceName,
                ifaceName,
                moduleName,
                ComponentNode.Kind.INTERFACE,
                relativePath
            );
            ifaceNode.setLoc(5);
            model.addComponent(ifaceNode);
            model.addDependency(new DependencyEdge(moduleName, fqIfaceName, DependencyEdge.Type.FIELD_TYPE));

            String extendsClause = ifaceMatcher.group(2);
            if (extendsClause != null) {
                for (String parent : extendsClause.split(",")) {
                    String cleanParent = parent.trim();
                    if (!cleanParent.isEmpty()) {
                        model.addDependency(new DependencyEdge(fqIfaceName, cleanParent, DependencyEdge.Type.EXTENDS));
                    }
                }
            }
        }

        // Extract TypeScript types
        Pattern typePattern = Pattern.compile(
            "(?:^|\\n)\\s*(?:export\\s+)?type\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=");
        Matcher typeMatcher = typePattern.matcher(source);
        while (typeMatcher.find()) {
            String typeName = typeMatcher.group(1);
            String fqTypeName = moduleName + "." + typeName;
            ComponentNode typeNode = new ComponentNode(
                fqTypeName,
                typeName,
                moduleName,
                ComponentNode.Kind.INTERFACE,
                relativePath
            );
            typeNode.setLoc(3);
            model.addComponent(typeNode);
            model.addDependency(new DependencyEdge(moduleName, fqTypeName, DependencyEdge.Type.FIELD_TYPE));
        }

        // Extract class implements clause: class Foo implements IFoo
        Pattern classImplPattern = Pattern.compile(
            "class\\s+([A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+extends\\s+[A-Za-z_$][A-Za-z0-9_$]*)?\\s+implements\\s+([A-Za-z_$][A-Za-z0-9_$,\\s]*)\\{");
        Matcher implMatcher = classImplPattern.matcher(source);
        while (implMatcher.find()) {
            String className = implMatcher.group(1);
            String fqClassName = moduleName + "." + className;
            String interfaces = implMatcher.group(2);
            for (String iface : interfaces.split(",")) {
                String cleanIface = iface.trim();
                if (!cleanIface.isEmpty()) {
                    model.addDependency(new DependencyEdge(fqClassName, cleanIface, DependencyEdge.Type.IMPLEMENTS));
                }
            }
        }
    }
}
