package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.RunEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RunEvidenceJpaRepository extends JpaRepository<RunEvidenceEntity, UUID> {

    Optional<RunEvidenceEntity> findFirstByExperimentIdOrderByCreatedAtDesc(UUID experimentId);
}
