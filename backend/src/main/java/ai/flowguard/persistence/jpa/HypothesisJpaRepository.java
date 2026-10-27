package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.HypothesisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HypothesisJpaRepository extends JpaRepository<HypothesisEntity, UUID> {
    java.util.List<HypothesisEntity> findByInvestigationIdOrderByCreatedAtAsc(UUID investigationId);
}
