package ch.nullprofile.billing.dto;

/**
 * Response DTO for donation summary statistics.
 * 
 * Contains information about:
 * - User's lifetime donation total
 * - Project total raised across all users
 * - Number of distinct supporters
 * 
 * All monetary amounts are in minor units (cents for EUR/USD).
 */
public class DonationSummaryResponse {

    private String currency;
    private long userTotalMinor;
    private long projectTotalMinor;
    private long supporterCount;

    public DonationSummaryResponse() {
    }

    public DonationSummaryResponse(String currency, long userTotalMinor, long projectTotalMinor, long supporterCount) {
        this.currency = currency;
        this.userTotalMinor = userTotalMinor;
        this.projectTotalMinor = projectTotalMinor;
        this.supporterCount = supporterCount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getUserTotalMinor() {
        return userTotalMinor;
    }

    public void setUserTotalMinor(long userTotalMinor) {
        this.userTotalMinor = userTotalMinor;
    }

    public long getProjectTotalMinor() {
        return projectTotalMinor;
    }

    public void setProjectTotalMinor(long projectTotalMinor) {
        this.projectTotalMinor = projectTotalMinor;
    }

    public long getSupporterCount() {
        return supporterCount;
    }

    public void setSupporterCount(long supporterCount) {
        this.supporterCount = supporterCount;
    }
}
