package com.pavankumar.shopnestecommercebackend;


import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractIntegrationTest {

    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("shopnest")
            .withUsername("shopnest_test")
            .withPassword("shopnest_test");

    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("JWT_SECRET", () -> "test-jwt-secret-for-integration-tests-only");
        registry.add("JWT_EXPIRATION", () -> "3600000");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "test-google-client-secret");
        registry.add("BREVO_API_KEY", () -> "test-brevo-api-key");
        registry.add("BREVO_SENDER_EMAIL", () -> "test@shopnest.local");
        registry.add("BREVO_SENDER_NAME", () -> "ShopNest Test");
        registry.add("RAZORPAY_KEY_ID", () -> "test-razorpay-key-id");
        registry.add("RAZORPAY_KEY_SECRET", () -> "test-razorpay-key-secret");
        registry.add("razorpay.webhook.secret", () -> "test-webhook-secret-12345");
        registry.add("APP_FRONTEND_URL", () -> "http://localhost:3000");
    }

    static {
        mysql.start();
        redis.start();
    }
}
