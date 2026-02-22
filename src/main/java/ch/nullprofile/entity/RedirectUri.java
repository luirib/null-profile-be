package ch.nullprofile.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redirect_uris")
public class RedirectUri {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "relying_party_id", nullable = false)
    private UUID relyingPartyId;

    @Column(name = "uri", nullable = false, length = 512)
    private String uri;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRelyingPartyId() {
        return relyingPartyId;
    }

    public void setRelyingPartyId(UUID relyingPartyId) {
        this.relyingPartyId = relyingPartyId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
