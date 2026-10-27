package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.EvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluationJpaRepository extends JpaRepository<EvaluationEntity, UUID> {
}
