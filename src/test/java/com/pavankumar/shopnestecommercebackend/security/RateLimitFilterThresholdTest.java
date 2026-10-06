package com.pavankumar.shopnestecommercebackend.security;

import com.pavankumar.shopnestecommercebackend.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterThresholdTest extends AbstractIntegrationTest {
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String CLIENT_IP = "10.10.10.10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void clearRateLimitKey() {
        redisTemplate.delete(rateLimitKey(CLIENT_IP));
    }

    @Test
    void authRequestsWithinLimit_areAllowed() throws Exception {
        for (int requestNumber = 1;
             requestNumber <= RateLimitFilter.AUTH_LIMIT;
             requestNumber++) {

            mockMvc.perform(
                            post(LOGIN_URL)
                                    .header("X-Forwarded-For", CLIENT_IP)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void sixthAuthRequest_isRejected() throws Exception {
        for (int requestNumber = 1;
             requestNumber <= RateLimitFilter.AUTH_LIMIT;
             requestNumber++) {

            mockMvc.perform(
                            post(LOGIN_URL)
                                    .header("X-Forwarded-For", CLIENT_IP)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")
                    )
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(
                        post(LOGIN_URL)
                                .header("X-Forwarded-For", CLIENT_IP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rateLimit_isIsolatedBetweenClientIps() throws Exception {
        for (int requestNumber = 1;
             requestNumber <= RateLimitFilter.AUTH_LIMIT;
             requestNumber++) {

            mockMvc.perform(
                            post(LOGIN_URL)
                                    .header("X-Forwarded-For", CLIENT_IP)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")
                    )
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(
                        post(LOGIN_URL)
                                .header("X-Forwarded-For", CLIENT_IP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isTooManyRequests());

        String secondClientIp = "10.10.10.11";
        mockMvc.perform(
                        post(LOGIN_URL)
                                .header("X-Forwarded-For", secondClientIp)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        redisTemplate.delete(rateLimitKey(secondClientIp));
    }

    @Test
    void rateLimit_expiresAndAllowsNewWindow() throws Exception {
        for (int requestNumber = 1;
             requestNumber <= RateLimitFilter.AUTH_LIMIT;
             requestNumber++) {

            mockMvc.perform(
                            post(LOGIN_URL)
                                    .header("X-Forwarded-For", CLIENT_IP)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")
                    )
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(
                        post(LOGIN_URL)
                                .header("X-Forwarded-For", CLIENT_IP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isTooManyRequests());

        String key = rateLimitKey(CLIENT_IP);
        Long ttlSeconds = redisTemplate.getExpire(
                key,
                TimeUnit.SECONDS
        );

        assertTrue(
                ttlSeconds != null
                        && ttlSeconds > 0
                        && ttlSeconds <= 60,
                "The rate-limit key must have a positive TTL within " +
                        "the configured one-minute window"
        );
        redisTemplate.expire(
                key,
                Duration.ofMillis(200)
        );
        Thread.sleep(300);

        mockMvc.perform(
                        post(LOGIN_URL)
                                .header("X-Forwarded-For", CLIENT_IP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }

    private String rateLimitKey(String clientIp) {
        return "rate_limit:ip: " + clientIp + ":" + LOGIN_URL;
    }
}