package com.matjussu.picsou.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Vérifie que les status d'erreur d'exception (404/503) sortent VRAIMENT via HTTP réel, et ne sont
 * pas masqués en 401 par le re-dispatch ERROR de Spring Security. MockMvc ne rejoue pas le forward
 * vers /error → ce test passe par un vrai serveur (RANDOM_PORT) pour couvrir ce chemin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ErrorDispatchIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired TestRestTemplate rest;

  private HttpHeaders authHeaders(String email) {
    SignupRequest signup = new SignupRequest(email, "Strong-Password-123", "Err");
    AuthResponse auth =
        rest.postForEntity("/api/auth/signup", signup, AuthResponse.class).getBody();
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(auth.accessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  void not_found_is_404_not_masked_as_401() {
    HttpHeaders headers = authHeaders("error-dispatch-404@picsou.demo");
    ResponseEntity<String> res =
        rest.exchange(
            "/api/goals/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void ai_unavailable_is_503_not_masked_as_401() {
    // Aucune clé Anthropic en test → AiUnavailableException → doit ressortir en 503 (pas 401).
    HttpHeaders headers = authHeaders("error-dispatch-503@picsou.demo");
    ResponseEntity<String> res =
        rest.exchange(
            "/api/insights/monthly",
            HttpMethod.POST,
            new HttpEntity<>("{}", headers),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
