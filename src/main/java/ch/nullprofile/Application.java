package ch.nullprofile;

import ch.nullprofile.config.WebAuthnProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRITICAL: HazelcastAutoConfiguration is excluded because we create our own
 * HazelcastInstance bean in SessionConfig for better control over embedded mode.
 * This prevents conflicts between Spring Boot auto-config and our custom Hazelcast setup.
 */
@SpringBootApplication(exclude = {HazelcastAutoConfiguration.class})
@EnableConfigurationProperties(WebAuthnProperties.class)
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
