package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.PatchAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatchAttemptJpaRepository extends JpaRepository<PatchAttemptEntity, UUID> {
}
