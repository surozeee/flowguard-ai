package ai.flowguard.persistence.jpa;

import ai.flowguard.persistence.entity.ModelCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModelCallJpaRepository extends JpaRepository<ModelCallEntity, UUID> {
}
