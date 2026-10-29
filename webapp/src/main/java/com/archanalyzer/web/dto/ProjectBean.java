package com.archanalyzer.web.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Advanced Java Module 5 (Java Beans): follows the strict JavaBean
 * convention — public no-arg constructor, private fields, public
 * get/set/is accessors, Serializable — so it can be used directly as a
 * <jsp:useBean> in JSP pages (see results.jsp) per Module 4's JSP
 * implicit-objects/scripting coverage.
 *
 * NOTE ON EJB (also Module 5): this application is a plain Servlet/Spring
 * web app, not deployed inside a full Jakarta EE application server, so
 * there is no EJB container to host genuine Session/Entity/Message-driven
 * beans. Implementing EJB here would mean either faking a container that
 * doesn't exist or requiring a WildFly/GlassFish deployment target that
 * contradicts the rest of the stack (PART 34: "do not create fake
 * functionality"). JavaBeans (this class) are used throughout instead,
 * which is the syllabus's simpler, broadly-applicable Module 5 concept;
 * full EJB is called out in the README as an intentionally out-of-scope
 * syllabus sub-topic.
 */
public class ProjectBean implements Serializable {

    private Long id;
    private String name;
    private String buildTool;
    private int fileCount;
    private int componentCount;
    private double architectureHealth;
    private double dependencyScore;
    private int cycleCount;
    private LocalDateTime analyzedAt;

    public ProjectBean() {
        // required no-arg constructor for JavaBean / <jsp:useBean> compatibility
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBuildTool() { return buildTool; }
    public void setBuildTool(String buildTool) { this.buildTool = buildTool; }

    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }

    public int getComponentCount() { return componentCount; }
    public void setComponentCount(int componentCount) { this.componentCount = componentCount; }

    public double getArchitectureHealth() { return architectureHealth; }
    public void setArchitectureHealth(double architectureHealth) { this.architectureHealth = architectureHealth; }

    public double getDependencyScore() { return dependencyScore; }
    public void setDependencyScore(double dependencyScore) { this.dependencyScore = dependencyScore; }

    public int getCycleCount() { return cycleCount; }
    public void setCycleCount(int cycleCount) { this.cycleCount = cycleCount; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
