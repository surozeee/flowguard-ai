package ai.flowguard.project.service.impl;

import ai.flowguard.project.payload.response.InvariantResponse;
import ai.flowguard.project.repository.ProjectRepository;
import ai.flowguard.project.service.ProjectService;
import ai.flowguard.core.exception.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public List<Map<String, Object>> listProjects() {
        return projectRepository.findAll();
    }

    @Override
    public Map<String, Object> getBySlug(String slug) {
        return projectRepository.findBySlug(slug);
    }

    @Override
    public List<InvariantResponse> listPublicInvariants(String slug) {
        // Slug reserved for future per-fixture invariant sets.
        return List.of(
                new InvariantResponse("JOURNAL_BALANCED", "Posted journal lines net to zero per currency."),
                new InvariantResponse("ACCOUNT_RECONCILES", "Stored balance equals opening plus posted movements."),
                new InvariantResponse("NONNEGATIVE", "Non-overdraft accounts remain non-negative."),
                new InvariantResponse("PAYMENT_EFFECT_ONCE", "Idempotency key produces at most one financial effect."),
                new InvariantResponse("VALID_STATE_TRANSITION", "Payment states follow allowed transitions."));
    }

    @Override
    public UUID resolveProjectId(String slug) {
        try {
            return projectRepository.findIdBySlug(slug);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("PROJECT_NOT_FOUND", "Unknown project slug: " + slug);
        }
    }
}
