package ai.flowguard.project.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.project.service.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@CustomLedgerRestController("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public GlobalResponse<?> list() {
        return GlobalResponse.success(projectService.listProjects());
    }

    @GetMapping("/{slug}")
    public GlobalResponse<?> getBySlug(@PathVariable String slug) {
        return GlobalResponse.success(projectService.getBySlug(slug));
    }

    @GetMapping("/{slug}/invariants")
    public GlobalResponse<?> invariants(@PathVariable String slug) {
        return GlobalResponse.success(projectService.listPublicInvariants(slug));
    }
}
