package ch.nullprofile.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Monthly authentication counter per Relying Party
 * 
 * Tracks the total number of successful authentications per RP per month.
 * This includes all authentication events, not just unique users (MAU).
 * 
 * Month is stored as DATE (first day of month) in Europe/Zurich timezone.
 */
@Entity
@Table(name = "rp_monthly_counters")
@IdClass(RpMonthlyCounterId.class)
public class RpMonthlyCounter {

    @Id
    @Column(name = "relying_party_id", nullable = false)
    private UUID relyingPartyId;

    @Id
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "auth_count", nullable = false)
    private Long authCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public RpMonthlyCounter() {
    }

    public RpMonthlyCounter(UUID relyingPartyId, LocalDate month, Long authCount) {
        this.relyingPartyId = relyingPartyId;
        this.month = month;
        this.authCount = authCount;
    }

    // Getters and Setters

    public UUID getRelyingPartyId() {
        return relyingPartyId;
    }

    public void setRelyingPartyId(UUID relyingPartyId) {
        this.relyingPartyId = relyingPartyId;
    }

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(LocalDate month) {
        this.month = month;
    }

    public Long getAuthCount() {
        return authCount;
    }

    public void setAuthCount(Long authCount) {
        this.authCount = authCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
