package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.InvestigationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InvestigationEventJpaRepository extends JpaRepository<InvestigationEventEntity, UUID> {

    List<InvestigationEventEntity> findByInvestigationIdOrderBySequenceNoAsc(UUID investigationId);

    @Query("""
            SELECT COALESCE(MAX(e.sequenceNo), 0) FROM InvestigationEventEntity e
            WHERE e.investigationId = :investigationId
            """)
    long maxSequenceNo(@Param("investigationId") UUID investigationId);
}
