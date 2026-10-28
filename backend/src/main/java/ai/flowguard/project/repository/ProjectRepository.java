package ai.flowguard.project.repository;

import ai.flowguard.persistence.entity.ProjectEntity;
import ai.flowguard.persistence.jpa.ProjectJpaRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ProjectRepository {

    private final ProjectJpaRepository jpa;

    public ProjectRepository(ProjectJpaRepository jpa) {
        this.jpa = jpa;
    }

    public List<Map<String, Object>> findAll() {
        return jpa.findAll().stream().map(this::toMap).toList();
    }

    public Map<String, Object> findBySlug(String slug) {
        return toMap(findEntityBySlug(slug));
    }

    public UUID findIdBySlug(String slug) {
        return findEntityBySlug(slug).getId();
    }

    private ProjectEntity findEntityBySlug(String slug) {
        return jpa.findBySlug(slug).orElseThrow(() -> new EmptyResultDataAccessException(1));
    }

    private Map<String, Object> toMap(ProjectEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("slug", entity.getSlug());
        row.put("fixtureVersion", entity.getFixtureVersion());
        return row;
    }
}
