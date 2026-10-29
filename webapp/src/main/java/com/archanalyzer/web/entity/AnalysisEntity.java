package com.archanalyzer.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analyses")
public class AnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "analyzed_at", insertable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "architecture_health")
    private Double architectureHealth;

    @Column(name = "dependency_score")
    private Double dependencyScore;

    @Column(name = "cycle_count")
    private Integer cycleCount;

    @Column(name = "total_components")
    private Integer totalComponents;

    @Column(name = "total_dependencies")
    private Integer totalDependencies;

    public AnalysisEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }

    public Double getArchitectureHealth() { return architectureHealth; }
    public void setArchitectureHealth(Double architectureHealth) { this.architectureHealth = architectureHealth; }

    public Double getDependencyScore() { return dependencyScore; }
    public void setDependencyScore(Double dependencyScore) { this.dependencyScore = dependencyScore; }

    public Integer getCycleCount() { return cycleCount; }
    public void setCycleCount(Integer cycleCount) { this.cycleCount = cycleCount; }

    public Integer getTotalComponents() { return totalComponents; }
    public void setTotalComponents(Integer totalComponents) { this.totalComponents = totalComponents; }

    public Integer getTotalDependencies() { return totalDependencies; }
    public void setTotalDependencies(Integer totalDependencies) { this.totalDependencies = totalDependencies; }
}
