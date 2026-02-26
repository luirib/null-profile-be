package ch.nullprofile.billing.dto;

/**
 * Response DTO for checkout session creation.
 */
public class CheckoutSessionResponse {

    private String url;

    public CheckoutSessionResponse() {
    }

    public CheckoutSessionResponse(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
