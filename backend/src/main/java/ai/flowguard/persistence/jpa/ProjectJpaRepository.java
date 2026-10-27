package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findBySlug(String slug);
}
