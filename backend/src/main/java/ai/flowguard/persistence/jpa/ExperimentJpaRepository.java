package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.ExperimentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExperimentJpaRepository extends JpaRepository<ExperimentEntity, UUID> {

    java.util.List<ExperimentEntity> findByInvestigationIdOrderByStartedAtAsc(UUID investigationId);
}
