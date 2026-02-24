package ch.nullprofile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oidc")
public class OidcProperties {

    private String issuer;
    private Security security = new Security();

    public static class Security {
        /**
         * Allow http://localhost redirect URIs for development
         * In production, only https should be allowed (except localhost)
         */
        private boolean allowHttpRedirectUrisForLocalhost = true;

        /**
         * Maximum authorization code validity in seconds
         */
        private int authCodeValiditySeconds = 300; // 5 minutes

        /**
         * Maximum session timeout in seconds
         */
        private int sessionTimeoutSeconds = 1800; // 30 minutes

        /**
         * Maximum length for state parameter
         */
        private int maxStateLength = 1024;

        /**
         * Maximum length for nonce parameter
         */
        private int maxNonceLength = 1024;

        /**
         * Minimum length for code_challenge (base64url encoded)
         */
        private int minCodeChallengeLength = 43;

        /**
         * Maximum length for code_challenge (base64url encoded)
         */
        private int maxCodeChallengeLength = 128;

        public boolean isAllowHttpRedirectUrisForLocalhost() {
            return allowHttpRedirectUrisForLocalhost;
        }

        public void setAllowHttpRedirectUrisForLocalhost(boolean allowHttpRedirectUrisForLocalhost) {
            this.allowHttpRedirectUrisForLocalhost = allowHttpRedirectUrisForLocalhost;
        }

        public int getAuthCodeValiditySeconds() {
            return authCodeValiditySeconds;
        }

        public void setAuthCodeValiditySeconds(int authCodeValiditySeconds) {
            this.authCodeValiditySeconds = authCodeValiditySeconds;
        }

        public int getSessionTimeoutSeconds() {
            return sessionTimeoutSeconds;
        }

        public void setSessionTimeoutSeconds(int sessionTimeoutSeconds) {
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        }

        public int getMaxStateLength() {
            return maxStateLength;
        }

        public void setMaxStateLength(int maxStateLength) {
            this.maxStateLength = maxStateLength;
        }

        public int getMaxNonceLength() {
            return maxNonceLength;
        }

        public void setMaxNonceLength(int maxNonceLength) {
            this.maxNonceLength = maxNonceLength;
        }

        public int getMinCodeChallengeLength() {
            return minCodeChallengeLength;
        }

        public void setMinCodeChallengeLength(int minCodeChallengeLength) {
            this.minCodeChallengeLength = minCodeChallengeLength;
        }

        public int getMaxCodeChallengeLength() {
            return maxCodeChallengeLength;
        }

        public void setMaxCodeChallengeLength(int maxCodeChallengeLength) {
            this.maxCodeChallengeLength = maxCodeChallengeLength;
        }
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }
}
