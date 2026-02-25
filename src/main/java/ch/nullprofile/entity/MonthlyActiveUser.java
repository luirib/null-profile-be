package ch.nullprofile.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Monthly Active User (MAU) tracking per Relying Party
 * 
 * Each row represents one unique user who authenticated to one specific RP in one specific month.
 * If the same end-user authenticates with multiple RPs, they are counted separately for each RP.
 * 
 * Month is stored as DATE (first day of month) in Europe/Zurich timezone.
 */
@Entity
@Table(name = "monthly_active_users")
@IdClass(MonthlyActiveUserId.class)
public class MonthlyActiveUser {

    @Id
    @Column(name = "relying_party_id", nullable = false)
    private UUID relyingPartyId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    // Constructors

    public MonthlyActiveUser() {
    }

    public MonthlyActiveUser(UUID relyingPartyId, UUID userId, LocalDate month, Instant firstSeenAt, Instant lastSeenAt) {
        this.relyingPartyId = relyingPartyId;
        this.userId = userId;
        this.month = month;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    // Getters and Setters

    public UUID getRelyingPartyId() {
        return relyingPartyId;
    }

    public void setRelyingPartyId(UUID relyingPartyId) {
        this.relyingPartyId = relyingPartyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(LocalDate month) {
        this.month = month;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
