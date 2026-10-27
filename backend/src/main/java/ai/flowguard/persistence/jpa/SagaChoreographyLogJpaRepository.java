package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.SagaChoreographyLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaChoreographyLogJpaRepository extends JpaRepository<SagaChoreographyLogEntity, UUID> {

    boolean existsBySagaIdAndEventTypeAndStep(UUID sagaId, String eventType, String step);

    List<SagaChoreographyLogEntity> findByIdempotencyKeyOrderByRecordedAtAsc(String idempotencyKey);

    List<SagaChoreographyLogEntity> findAllByOrderByRecordedAtDesc(Pageable pageable);

    long count();
}
