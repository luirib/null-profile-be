package ch.nullprofile.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class SessionConfig {

    @Value("${session.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${session.cookie.secure:false}")
    private boolean cookieSecure;

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setClusterName("nullprofile-session-cluster");
        
        // Disable multicast and TCP/IP join for embedded mode
        NetworkConfig networkConfig = config.getNetworkConfig();
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        
        return config;
    }

    /**
     * Configure session cookie for cross-origin requests
     * 
     * CRITICAL: For session-based authentication with CORS, the session cookie
     * must be configured to allow cross-origin cookie transmission.
     * 
     * Development (localhost): SameSite=Lax works because browser treats localhost specially
     * Production (different domains): Must use SameSite=None with Secure=true
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        
        // Use standard JSESSIONID name (Hazelcast compatible)
        serializer.setCookieName("JSESSIONID");
        
        // Cookie path
        serializer.setCookiePath("/");
        
        // SameSite setting - CRITICAL for CORS
        // Development: "Lax" (works for localhost)
        // Production: "None" (requires Secure=true and HTTPS)
        serializer.setSameSite(cookieSameSite);
        
        // Secure flag (required for SameSite=None)
        serializer.setUseSecureCookie(cookieSecure);
        
        // HttpOnly for security (prevents JavaScript access)
        serializer.setUseHttpOnlyCookie(true);
        
        return serializer;
    }
}
