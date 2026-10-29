package com.archanalyzer.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Java Module 7 (Java Persistence: "Hibernate Annotation",
 * "Hibernate O/R Mapping"). Maps directly onto the `projects` table defined
 * in sql/schema.sql.
 */
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_archive_name")
    private String originalArchiveName;

    @Column(name = "build_tool")
    private String buildTool;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnalysisEntity> analyses = new ArrayList<>();

    public ProjectEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOriginalArchiveName() { return originalArchiveName; }
    public void setOriginalArchiveName(String originalArchiveName) { this.originalArchiveName = originalArchiveName; }

    public String getBuildTool() { return buildTool; }
    public void setBuildTool(String buildTool) { this.buildTool = buildTool; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<AnalysisEntity> getAnalyses() { return analyses; }
}
