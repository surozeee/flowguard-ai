package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.InvestigationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvestigationJpaRepository extends JpaRepository<InvestigationEntity, UUID> {
}
