-- ============================================================================
-- Java Architecture Quality Assessment & Automated Refactoring Analyzer
-- MySQL schema (PART 25). Run with:  mysql -u root -p < schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS archanalyzer CHARACTER SET utf8mb4;
USE archanalyzer;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_archive_name VARCHAR(255),
    build_tool VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_projects_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE project_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    language VARCHAR(32) NOT NULL,
    analysis_depth ENUM('Full','Partial') NOT NULL,
    loc INT DEFAULT 0,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_files_project (project_id)
) ENGINE=InnoDB;

CREATE TABLE analyses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    architecture_health DECIMAL(5,1),
    dependency_score DECIMAL(5,1),
    cycle_count INT,
    total_components INT,
    total_dependencies INT,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_analyses_project (project_id)
) ENGINE=InnoDB;

CREATE TABLE components (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    component_key VARCHAR(512) NOT NULL, -- fully qualified name
    simple_name VARCHAR(255),
    package_name VARCHAR(255),
    kind VARCHAR(32),
    loc INT,
    cyclomatic_complexity INT,
    method_count INT,
    field_count INT,
    fan_in INT,
    fan_out INT,
    centrality DECIMAL(6,4),
    in_cycle BOOLEAN DEFAULT FALSE,
    ml_risk ENUM('LOW','MEDIUM','HIGH'),
    ml_confidence DECIMAL(5,4),
    FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE,
    INDEX idx_components_analysis (analysis_id),
    INDEX idx_components_risk (ml_risk)
) ENGINE=InnoDB;

CREATE TABLE dependencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    from_component VARCHAR(512) NOT NULL,
    to_component VARCHAR(512) NOT NULL,
    dependency_type VARCHAR(32),
    FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE,
    INDEX idx_dependencies_analysis (analysis_id),
    INDEX idx_dependencies_from (from_component),
    INDEX idx_dependencies_to (to_component)
) ENGINE=InnoDB;

CREATE TABLE architecture_smells (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    component_key VARCHAR(512) NOT NULL,
    smell_type VARCHAR(64) NOT NULL,
    severity ENUM('LOW','MEDIUM','HIGH') NOT NULL,
    evidence TEXT,
    FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE,
    INDEX idx_smells_analysis (analysis_id)
) ENGINE=InnoDB;

CREATE TABLE impact_analyses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    component_key VARCHAR(512) NOT NULL,
    direct_impact_count INT,
    indirect_impact_count INT,
    max_depth INT,
    impact_score DECIMAL(5,1),
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE,
    INDEX idx_impact_analysis (analysis_id)
) ENGINE=InnoDB;

CREATE TABLE architecture_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    strategy_name VARCHAR(128) NOT NULL,
    target_component VARCHAR(512),
    rationale TEXT,
    projected_dependency_score DECIMAL(5,1),
    projected_architecture_health DECIMAL(5,1),
    projected_cycles INT,
    files_changed INT,
    estimated_cost_hours INT,
    composite_score DECIMAL(8,2),
    status ENUM('PROPOSED','REJECTED','ACCEPTED','VALIDATED_PASS','VALIDATED_WARNING','VALIDATED_FAIL') DEFAULT 'PROPOSED',
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE,
    INDEX idx_candidates_analysis (analysis_id),
    INDEX idx_candidates_status (status)
) ENGINE=InnoDB;

CREATE TABLE candidate_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    dependency_term DECIMAL(8,2),
    cycle_term DECIMAL(8,2),
    cost_term DECIMAL(8,2),
    files_term DECIMAL(8,2),
    maintainability_term DECIMAL(8,2),
    total DECIMAL(8,2),
    FOREIGN KEY (candidate_id) REFERENCES architecture_candidates(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE refactoring_changes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    description TEXT,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES architecture_candidates(id) ON DELETE CASCADE,
    INDEX idx_changes_candidate (candidate_id)
) ENGINE=InnoDB;

CREATE TABLE validation_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    build_status ENUM('PASS','FAIL','SKIPPED'),
    tests_passed INT,
    tests_total INT,
    verdict ENUM('PASS','WARNING','FAIL'),
    summary TEXT,
    architecture_health_before DECIMAL(5,1),
    architecture_health_after DECIMAL(5,1),
    dependency_reduction_percent DECIMAL(5,1),
    cycle_reduction_percent DECIMAL(5,1),
    validated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES architecture_candidates(id) ON DELETE CASCADE,
    INDEX idx_validation_candidate (candidate_id)
) ENGINE=InnoDB;

CREATE TABLE project_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    candidate_id BIGINT,
    version_label VARCHAR(64),
    zip_storage_path VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id) REFERENCES architecture_candidates(id) ON DELETE SET NULL,
    INDEX idx_versions_project (project_id)
) ENGINE=InnoDB;

CREATE TABLE reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    analysis_id BIGINT,
    content_markdown LONGTEXT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE SET NULL,
    INDEX idx_reports_project (project_id)
) ENGINE=InnoDB;

-- ============================================================================
-- Stored procedure used to demonstrate CallableStatement (Advanced Java
-- Module 1) from ProjectDao.countProjectsForUser(). Returns the number of
-- projects owned by a given user via an OUT parameter.
-- ============================================================================
DELIMITER $$
CREATE PROCEDURE sp_count_projects_for_user(IN p_user_id BIGINT, OUT p_count INT)
BEGIN
    SELECT COUNT(*) INTO p_count FROM projects WHERE user_id = p_user_id;
END$$
DELIMITER ;

CREATE TABLE jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    job_type VARCHAR(64) NOT NULL, -- ANALYZE, REFACTOR, VALIDATE, PACKAGE
    status ENUM('PENDING','RUNNING','COMPLETED','FAILED') DEFAULT 'PENDING',
    progress_percent INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_jobs_project (project_id),
    INDEX idx_jobs_status (status)
) ENGINE=InnoDB;
