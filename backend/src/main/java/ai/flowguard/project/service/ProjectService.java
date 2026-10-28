package ai.flowguard.project.service;

import ai.flowguard.project.payload.response.InvariantResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProjectService {

    List<Map<String, Object>> listProjects();

    Map<String, Object> getBySlug(String slug);

    List<InvariantResponse> listPublicInvariants(String slug);

    UUID resolveProjectId(String slug);
}
