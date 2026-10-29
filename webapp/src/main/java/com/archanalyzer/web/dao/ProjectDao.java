package com.archanalyzer.web.dao;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Advanced Java Module 1 (Advanced concepts of Core Java: "JDBC, Drivers,
 * Packages, JDBC process, Database Connection, Statements — Prepared
 * statement, Callable statement, ResultSet — Transaction processing").
 * ---------------------------------------------------------------------------
 * This class deliberately uses RAW JDBC (not Hibernate) so the syllabus's
 * JDBC module is demonstrated directly and explicitly, even though the rest
 * of the application's persistence goes through JPA/Hibernate (Module 7) —
 * both are real, both are used, for different, syllabus-mapped reasons.
 */
@Repository
public class ProjectDao {

    private final DataSource dataSource;

    public ProjectDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Inserts a project row AND its first file row in a single manual
     * transaction (Module 1: "Transaction processing") — either both
     * succeed or neither does.
     */
    public long insertProjectWithFirstFile(long userId, String projectName, String firstFilePath,
                                             String language, int loc) throws SQLException {
        String insertProjectSql = "INSERT INTO projects (user_id, name) VALUES (?, ?)";
        String insertFileSql = "INSERT INTO project_files (project_id, relative_path, language, analysis_depth, loc) " +
            "VALUES (?, ?, ?, 'Full', ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // begin transaction
            try {
                long projectId;
                try (PreparedStatement ps = conn.prepareStatement(insertProjectSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, userId);
                    ps.setString(2, projectName);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Failed to obtain generated project id");
                        projectId = keys.getLong(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(insertFileSql)) {
                    ps.setLong(1, projectId);
                    ps.setString(2, firstFilePath);
                    ps.setString(3, language);
                    ps.setInt(4, loc);
                    ps.executeUpdate();
                }
                conn.commit();
                return projectId;
            } catch (SQLException e) {
                conn.rollback(); // Module 1: rollback on failure keeps the transaction atomic
                throw e;
            }
        }
    }

    /**
     * Calls the sp_count_projects_for_user stored procedure via
     * CallableStatement with an OUT parameter (Module 1: "Callable statement").
     */
    public int countProjectsForUser(long userId) throws SQLException {
        String call = "{call sp_count_projects_for_user(?, ?)}";
        try (Connection conn = dataSource.getConnection();
             CallableStatement cs = conn.prepareCall(call)) {
            cs.setLong(1, userId);
            cs.registerOutParameter(2, Types.INTEGER);
            cs.execute();
            return cs.getInt(2);
        }
    }

    /** Simple parameterized SELECT (Module 1: "Prepared statement", "ResultSet"). */
    public boolean projectNameExistsForUser(long userId, String name) throws SQLException {
        String sql = "SELECT 1 FROM projects WHERE user_id = ? AND name = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
