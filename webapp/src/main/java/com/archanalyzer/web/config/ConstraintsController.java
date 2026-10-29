package com.archanalyzer.web.config;

import com.archanalyzer.optimization.ConstraintSet;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;

/**
 * PART 16 (User Constraints) as a Spring MVC form-backed view controller —
 * the concrete "form submission / validating form input" demonstration
 * Module 6 asks for. GET renders the form bound to a fresh ConstraintForm;
 * POST validates it and, on success, builds the real ConstraintSet the
 * optimization engine (com.archanalyzer.optimization) consumes.
 */
@Controller
public class ConstraintsController {

    @GetMapping("/constraints")
    public String showForm(Model model) {
        model.addAttribute("constraintForm", new ConstraintForm());
        return "constraints"; // -> /WEB-INF/jsp/constraints.jsp
    }

    @PostMapping("/constraints")
    public String submitForm(@Valid @ModelAttribute ConstraintForm form, BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            return "constraints";
        }
        ConstraintSet constraints = new ConstraintSet();
        constraints.maxFilesChanged = form.getMaxFilesChanged();
        constraints.noNewExternalDependencies = form.isNoNewExternalDependencies();
        constraints.preservePublicApi = form.isPreservePublicApi();
        constraints.preferLowRisk = form.isPreferLowRisk();

        model.addAttribute("savedConstraints", constraints);
        return "constraints-saved";
    }

    /** Form-backing bean with bean-validation annotations (Module 6: "Validating form input"). */
    public static class ConstraintForm {
        @Min(value = 1, message = "Must allow changing at least 1 file")
        @Max(value = 200, message = "Maximum files changed is unreasonably high")
        private int maxFilesChanged = 15;

        private boolean noNewExternalDependencies = true;
        private boolean preservePublicApi = true;
        private boolean preferLowRisk = true;

        public int getMaxFilesChanged() { return maxFilesChanged; }
        public void setMaxFilesChanged(int maxFilesChanged) { this.maxFilesChanged = maxFilesChanged; }
        public boolean isNoNewExternalDependencies() { return noNewExternalDependencies; }
        public void setNoNewExternalDependencies(boolean v) { this.noNewExternalDependencies = v; }
        public boolean isPreservePublicApi() { return preservePublicApi; }
        public void setPreservePublicApi(boolean v) { this.preservePublicApi = v; }
        public boolean isPreferLowRisk() { return preferLowRisk; }
        public void setPreferLowRisk(boolean v) { this.preferLowRisk = v; }
    }
}
