package ch.nullprofile.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class SessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);

    @Value("${session.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${session.cookie.secure:false}")
    private boolean cookieSecure;

    @Bean
    public Config hazelcastConfig() {
        logger.info("[SESSION-CONFIG] Initializing Hazelcast session storage (embedded mode)");
        
        Config config = new Config();
        config.setClusterName("nullprofile-session-cluster");
        
        // Disable multicast and TCP/IP join for embedded mode
        NetworkConfig networkConfig = config.getNetworkConfig();
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        
        logger.info("[SESSION-CONFIG] Hazelcast configured: cluster=nullprofile-session-cluster, mode=embedded");
        logger.info("[SESSION-CONFIG] Session timeout: 1800 seconds (30 minutes)");
        
        return config;
    }

    /**
     * Configure session cookie for cross-origin requests
     * 
     * CRITICAL: For session-based authentication with CORS, the session cookie
     * must be configured to allow cross-origin cookie transmission.
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        logger.info("[SESSION-COOKIE] Configuring session cookie serializer");
        logger.info("[SESSION-COOKIE] Cookie name: JSESSIONID");
        logger.info("[SESSION-COOKIE] SameSite: {} (from env: SESSION_COOKIE_SAME_SITE)", cookieSameSite);
        logger.info("[SESSION-COOKIE] Secure: {} (from env: SESSION_COOKIE_SECURE)", cookieSecure);
        logger.info("[SESSION-COOKIE] HttpOnly: true (hardcoded for security)");
        logger.info("[SESSION-COOKIE] Path: / (all paths)");
        
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
        
        // Validate configuration
        if ("None".equalsIgnoreCase(cookieSameSite) && !cookieSecure) {
            logger.warn("[SESSION-COOKIE] *** CONFIGURATION WARNING ***");
            logger.warn("[SESSION-COOKIE] SameSite=None requires Secure=true!");
            logger.warn("[SESSION-COOKIE] Browsers will REJECT cookies with SameSite=None and Secure=false");
            logger.warn("[SESSION-COOKIE] Set SESSION_COOKIE_SECURE=true in environment variables");
        }
        
        if (!cookieSecure && cookieSameSite.equalsIgnoreCase("None")) {
            logger.error("[SESSION-COOKIE] *** INVALID CONFIGURATION DETECTED ***");
            logger.error("[SESSION-COOKIE] This configuration WILL BREAK cross-origin authentication!");
        }
        
        logger.info("[SESSION-COOKIE] Configuration complete");
        
        return serializer;
    }
}
