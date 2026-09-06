package com.pavankumar.shopnestecommercebackend.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;


@Component
@RequiredArgsConstructor
public class RazorpayGateway {

    private final ObjectMapper objectMapper;

    @Value("${razorpay.key.id}")
    private String key;

    @Value("${razorpay.key.secret}")
    private String secretKey;

    @Value("${razorpay.api.base-url}")
    private String baseUrl;

    private RestClient restClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public record RefundResponse(
            String refundId,
            String status
    ) {
    }

    public RefundResponse createRefund(
            String razorpayPaymentId,
            BigDecimal amount,
            String idempotencyKey) {

        long amountInPaise = amount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        Map<String, Object> requestBody = Map.of(
                "amount", amountInPaise
        );

        String credentials = key + ":" + secretKey;

        String basicAuth = Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8)
                );

        String responseBody = restClient.post()
                .uri(
                        "/payments/{paymentId}/refund",
                        razorpayPaymentId
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + basicAuth
                )
                .header(
                        "X-Refund-Idempotency",
                        idempotencyKey
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode response =
                    objectMapper.readTree(responseBody);

            String refundId =
                    response.get("id").asText();

            String status =
                    response.get("status").asText();

            return new RefundResponse(
                    refundId,
                    status
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to read Razorpay refund response",
                    e
            );

        }

    }

    public RefundResponse fetchRefund(String razorpayRefundId){

        String credentials = key + ":" + secretKey;

        String basicAuth = Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8)
                );

        String responseBody = restClient.get()
                .uri(
                        "/refunds/{refundId}",
                        razorpayRefundId
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + basicAuth
                )
                .retrieve()
                .body(String.class);

        try {

            JsonNode response =
                    objectMapper.readTree(responseBody);

            String refundId =
                    response.get("id").asText();

            String status =
                    response.get("status").asText();

            return new RefundResponse(
                    refundId,
                    status
            );

        }

        catch (Exception e) {

            throw new RuntimeException(
                    "Unable to read Razorpay refund status",
                    e
            );
        }
    }

}
