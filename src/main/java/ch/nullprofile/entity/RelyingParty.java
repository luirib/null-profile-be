package ch.nullprofile.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "relying_parties")
public class RelyingParty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rp_id", nullable = false, unique = true, length = 255)
    private String rpId;

    @Column(name = "rp_name", nullable = false, length = 255)
    private String rpName;

    @Column(name = "sector_id", nullable = false, length = 255)
    private String sectorId;

    @Column(name = "branding_logo_url", length = 512)
    private String brandingLogoUrl;

    @Column(name = "branding_primary_color", length = 7)
    private String brandingPrimaryColor;

    @Column(name = "branding_secondary_color", length = 7)
    private String brandingSecondaryColor;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "plan_tier", nullable = false, length = 50)
    private String planTier = "FREE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName;
    }

    public String getSectorId() {
        return sectorId;
    }

    public void setSectorId(String sectorId) {
        this.sectorId = sectorId;
    }

    public String getBrandingLogoUrl() {
        return brandingLogoUrl;
    }

    public void setBrandingLogoUrl(String brandingLogoUrl) {
        this.brandingLogoUrl = brandingLogoUrl;
    }

    public String getBrandingPrimaryColor() {
        return brandingPrimaryColor;
    }

    public void setBrandingPrimaryColor(String brandingPrimaryColor) {
        this.brandingPrimaryColor = brandingPrimaryColor;
    }

    public String getBrandingSecondaryColor() {
        return brandingSecondaryColor;
    }

    public void setBrandingSecondaryColor(String brandingSecondaryColor) {
        this.brandingSecondaryColor = brandingSecondaryColor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPlanTier() {
        return planTier;
    }

    public void setPlanTier(String planTier) {
        this.planTier = planTier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }
}
