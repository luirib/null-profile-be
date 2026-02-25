package ch.nullprofile.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for billing system.
 * 
 * Supports multiple billing modes:
 * - DONATION: One-time donations via Stripe Checkout
 * - SUBSCRIPTION: Recurring subscriptions (future)
 * - DISABLED: Billing completely disabled
 */
@Component
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    /**
     * Billing mode determines how the system handles payments
     */
    private Mode mode = Mode.DONATION;

    /**
     * Default currency for payments (ISO 4217 currency code)
     * Configured via BILLING_CURRENCY environment variable
     */
    private String currency;

    /**
     * URL to redirect to after successful payment
     * Configured via BILLING_SUCCESS_URL environment variable
     */
    private String successUrl;

    /**
     * URL to redirect to when payment is cancelled
     * Configured via BILLING_CANCEL_URL environment variable
     */
    private String cancelUrl;

    public enum Mode {
        DONATION,
        SUBSCRIPTION,
        DISABLED
    }

    // Getters and Setters

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public boolean isEnabled() {
        return mode != Mode.DISABLED;
    }
}
