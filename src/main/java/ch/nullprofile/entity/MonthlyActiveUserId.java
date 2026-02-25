package ch.nullprofile.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for MonthlyActiveUser entity
 */
public class MonthlyActiveUserId implements Serializable {

    private UUID relyingPartyId;
    private UUID userId;
    private LocalDate month;

    public MonthlyActiveUserId() {
    }

    public MonthlyActiveUserId(UUID relyingPartyId, UUID userId, LocalDate month) {
        this.relyingPartyId = relyingPartyId;
        this.userId = userId;
        this.month = month;
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

    // equals and hashCode required for composite key

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MonthlyActiveUserId that = (MonthlyActiveUserId) o;
        return Objects.equals(relyingPartyId, that.relyingPartyId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(month, that.month);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relyingPartyId, userId, month);
    }
}
