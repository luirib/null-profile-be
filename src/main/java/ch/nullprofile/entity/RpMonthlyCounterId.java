package ch.nullprofile.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for RpMonthlyCounter entity
 */
public class RpMonthlyCounterId implements Serializable {

    private UUID relyingPartyId;
    private LocalDate month;

    public RpMonthlyCounterId() {
    }

    public RpMonthlyCounterId(UUID relyingPartyId, LocalDate month) {
        this.relyingPartyId = relyingPartyId;
        this.month = month;
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

    // equals and hashCode required for composite key

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RpMonthlyCounterId that = (RpMonthlyCounterId) o;
        return Objects.equals(relyingPartyId, that.relyingPartyId) &&
                Objects.equals(month, that.month);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relyingPartyId, month);
    }
}
