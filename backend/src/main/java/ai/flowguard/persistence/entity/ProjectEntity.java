package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects", schema = MetadataSchema.NAME)
public class ProjectEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "fixture_version", nullable = false, length = 40)
    private String fixtureVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getFixtureVersion() {
        return fixtureVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
