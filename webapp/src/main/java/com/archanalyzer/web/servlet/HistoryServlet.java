package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.web.dto.JsonUtil;
import com.archanalyzer.web.history.ProjectHistoryManager;
import com.archanalyzer.web.history.ProjectHistoryManager.HistoryEntry;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@WebServlet(name = "HistoryServlet", urlPatterns = {"/history", "/projects/select", "/api/projects/history"})
public class HistoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();

        // 1. API route: /api/projects/history
        if ("/api/projects/history".equals(servletPath)) {
            handleApiHistory(req, resp);
            return;
        }

        // 2. Select route: /projects/select or /history?select=...
        String selectId = req.getParameter("select");
        if (selectId == null && "/projects/select".equals(servletPath)) {
            selectId = req.getParameter("id");
        }

        if (selectId != null && !selectId.isBlank()) {
            handleSelectProject(selectId, req, resp);
            return;
        }

        // 3. Delete route: /history?delete=...
        String deleteId = req.getParameter("delete");
        if (deleteId != null && !deleteId.isBlank()) {
            ProjectHistoryManager.deleteById(deleteId);
            resp.sendRedirect(req.getContextPath() + "/history");
            return;
        }

        // 4. View history page: /history
        HttpSession session = req.getSession(false);
        PipelineResult activeResult = session != null
            ? (PipelineResult) session.getAttribute("lastPipelineResult") : null;
        Path activeDir = session != null
            ? (Path) session.getAttribute("activeUploadTempDir") : null;

        if (activeResult != null) {
            ProjectHistoryManager.ensureRecorded(activeResult, activeDir);
        }

        req.setAttribute("historyList", ProjectHistoryManager.getHistory());
        req.setAttribute("activeResult", activeResult);
        req.getRequestDispatcher("/WEB-INF/jsp/history.jsp").forward(req, resp);
    }

    private void handleSelectProject(String id, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HistoryEntry entry = ProjectHistoryManager.getById(id);
        if (entry != null && entry.getResult() != null) {
            HttpSession session = req.getSession(true);
            session.setAttribute("lastPipelineResult", entry.getResult());
            if (entry.getWorkDir() != null) {
                session.setAttribute("activeUploadTempDir", entry.getWorkDir());
            }
            String target = "simulator".equalsIgnoreCase(req.getParameter("goto")) ? "/simulator" : "/dashboard";
            resp.sendRedirect(req.getContextPath() + target);
        } else {
            resp.sendRedirect(req.getContextPath() + "/history?error=notfound");
        }
    }

    private void handleApiHistory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        List<HistoryEntry> list = ProjectHistoryManager.getHistory();
        List<Map<String, Object>> out = new ArrayList<>();
        HttpSession session = req.getSession(false);
        PipelineResult active = session != null
            ? (PipelineResult) session.getAttribute("lastPipelineResult") : null;
        String activeName = (active != null && active.model != null) ? active.model.getProjectName() : null;

        for (HistoryEntry e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("name", e.getProjectName());
            m.put("archive", e.getArchiveFileName());
            m.put("date", e.getFormattedDate());
            m.put("files", e.getFileCount());
            m.put("components", e.getComponentCount());
            m.put("health", e.getArchitectureHealth());
            m.put("isActive", activeName != null && activeName.equalsIgnoreCase(e.getProjectName()));
            out.add(m);
        }
        resp.getWriter().write(JsonUtil.toJson(out));
    }
}
