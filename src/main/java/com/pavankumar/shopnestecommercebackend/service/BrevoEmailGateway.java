package com.pavankumar.shopnestecommercebackend.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailGateway {

    private final ObjectMapper objectMapper;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.api.base-url}")
    private String baseUrl;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record EmailSendResult(boolean success, String messageId, String errorMessage) {}

    public EmailSendResult sendEmail(String toEmail, String toName, String subject, String htmlContent){

        Map<String, Object> requestBody=Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", toEmail, "name", toName)),
                "subject", subject,
                "htmlContent", htmlContent
        );

        try{
            String responseBody = restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            String messageId = response.has("messageId") ? response.get("messageId").asText() : null;
            return new EmailSendResult(true, messageId, null);
        }
        catch (HttpClientErrorException e) {
            log.error("Brevo rejected the request ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new EmailSendResult(false, null, e.getResponseBodyAsString());

        } catch (HttpServerErrorException e) {
            log.error("Brevo service error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new EmailSendResult(false, null, e.getResponseBodyAsString());

        } catch (Exception e) {
            log.error("Unexpected failure calling Brevo", e);
            return new EmailSendResult(false, null, e.getMessage());
        }
    }
}
