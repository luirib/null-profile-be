package ch.nullprofile.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    private static final Logger logger = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${cors.allowed.origins}")
    private String allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Parse comma-separated origins from environment variable
                String[] origins = allowedOrigins.split(",");
                
                // Trim whitespace from each origin
                for (int i = 0; i < origins.length; i++) {
                    origins[i] = origins[i].trim();
                }
                
                logger.info("[CORS-CONFIG] ========================================");
                logger.info("[CORS-CONFIG] Configuring CORS with allowed origins:");
                for (String origin : origins) {
                    logger.info("[CORS-CONFIG]   - {}", origin);
                }
                logger.info("[CORS-CONFIG] Methods: GET, POST, PUT, DELETE, OPTIONS");
                logger.info("[CORS-CONFIG] Allow credentials: TRUE (required for session cookies)");
                logger.info("[CORS-CONFIG] Max age: 3600 seconds");
                logger.info("[CORS-CONFIG] ========================================");
                
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
                
                logger.info("[CORS-CONFIG] CORS configuration complete");
                logger.info("[CORS-CONFIG] Verify frontend origin matches one of the allowed origins above");
            }
        };
    }
}
