package ch.nullprofile.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session configuration using Hazelcast as distributed session store.
 * 
 * IMPORTANT FOR RENDER DEPLOYMENT:
 * - Spring Boot HazelcastAutoConfiguration is EXCLUDED in Application.java
 * - We create our own HazelcastInstance bean here for explicit control
 * - Embedded mode: single-node Hazelcast running in same JVM
 * - Works for both local development and Render single-container deployment
 * - For multi-instance clustering on Render, additional network config needed
 * 
 * Session storage: Hazelcast in-memory distributed map
 * DO NOT switch to JDBC session storage - keep Hazelcast!
 */
@Configuration
@EnableHazelcastHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class SessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);

    @Value("${session.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${session.cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * Creates HazelcastInstance bean for Spring Session.
     * This is the ONLY place where HazelcastInstance is created.
     * Spring Boot auto-config is disabled to prevent conflicts.
     */
    @Bean(destroyMethod = "shutdown")
    public HazelcastInstance hazelcastInstance() {
        logger.info("[HAZELCAST] ========================================");
        logger.info("[HAZELCAST] Creating HazelcastInstance for Spring Session");
        logger.info("[HAZELCAST] Mode: Embedded (single-node in-memory)");
        logger.info("[HAZELCAST] Spring Boot Auto-Config: DISABLED (using custom config)");
        
        Config config = createHazelcastConfig();
        
        try {
            HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
            logger.info("[HAZELCAST] ✓ HazelcastInstance created successfully");
            logger.info("[HAZELCAST] Instance name: {}", instance.getName());
            logger.info("[HAZELCAST] Cluster name: {}", instance.getConfig().getClusterName());
            logger.info("[HAZELCAST] Local member: {}", instance.getCluster().getLocalMember().getAddress());
            logger.info("[HAZELCAST] Cluster size: {} member(s)", instance.getCluster().getMembers().size());
            logger.info("[HAZELCAST] Session map: spring:session:sessions");
            logger.info("[HAZELCAST] ========================================");
            return instance;
        } catch (Exception e) {
            logger.error("[HAZELCAST] ✗ FAILED to create HazelcastInstance", e);
            logger.error("[HAZELCAST] Error type: {}", e.getClass().getName());
            logger.error("[HAZELCAST] Error message: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("[HAZELCAST] Caused by: {}", e.getCause().getMessage());
            }
            logger.error("[HAZELCAST] ========================================");
            throw new RuntimeException("Failed to initialize Hazelcast for session storage", e);
        }
    }
    
    /**
     * Creates Hazelcast configuration for embedded single-node deployment.
     * Optimized for Render's single-container environment.
     */
    private Config createHazelcastConfig() {
        logger.info("[HAZELCAST-CONFIG] Building configuration...");
        
        Config config = new Config();
        config.setClusterName("nullprofile-session-cluster");
        
        // Unique instance name to avoid conflicts
        String instanceName = "nullprofile-hz-" + System.currentTimeMillis();
        config.setInstanceName(instanceName);
        logger.info("[HAZELCAST-CONFIG] Instance name: {}", instanceName);
        logger.info("[HAZELCAST-CONFIG] Cluster name: nullprofile-session-cluster");
        
        // Network configuration for embedded single-node
        NetworkConfig networkConfig = config.getNetworkConfig();
        
        // Disable all cluster join mechanisms (we want single-node embedded)
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getAutoDetectionConfig().setEnabled(false);
        logger.info("[HAZELCAST-CONFIG] Cluster join: DISABLED (single-node embedded)");
        
        // Auto-assign available port (avoids conflicts on Render)
        networkConfig.setPort(0);
        networkConfig.setPortAutoIncrement(true);
        networkConfig.setPortCount(100);
        logger.info("[HAZELCAST-CONFIG] Network port: auto-assign (0)");
        
        // Disable REST API for security
        networkConfig.getRestApiConfig().setEnabled(false);
        
        // Cloud-optimized properties
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.shutdownhook.enabled", "false");
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty("hazelcast.operation.call.timeout.millis", "60000");
        config.setProperty("hazelcast.socket.bind.any", "false");
        config.setProperty("hazelcast.initial.min.cluster.size", "1");
        logger.info("[HAZELCAST-CONFIG] Properties: phone-home=false, logging=slf4j");
        
        logger.info("[HAZELCAST-CONFIG] Configuration complete");
        logger.info("[HAZELCAST-CONFIG] Session timeout: 1800 seconds (30 minutes)");
        
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
