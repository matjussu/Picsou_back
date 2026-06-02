package com.matjussu.picsou.ocr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.ai.AiUnavailableException;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** OCR : le client vision est stubé (@MockitoBean) → jamais d'appel à l'API réelle. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OcrControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean OcrClient ocrClient;

  private String signup(String email) throws Exception {
    var req = new SignupRequest(email, "Strong-Password-123", "Ocr");
    var res =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(res.getResponse().getContentAsString(), AuthResponse.class).accessToken();
  }

  private MockMultipartFile png() {
    return new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3, 4});
  }

  @Test
  void extracts_receipt_fields() throws Exception {
    when(ocrClient.extractReceipt(any(), eq("image/png")))
        .thenReturn(
            new ReceiptExtraction(
                new BigDecimal("23.90"), "Monoprix", LocalDate.parse("2026-05-10")));
    String token = signup("ocr-ok@picsou.demo");

    mvc.perform(
            multipart("/api/ocr/receipt").file(png()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(23.90))
        .andExpect(jsonPath("$.merchant").value("Monoprix"))
        .andExpect(jsonPath("$.date").value("2026-05-10"));
  }

  @Test
  void partial_extraction_does_not_fail() throws Exception {
    when(ocrClient.extractReceipt(any(), any()))
        .thenReturn(new ReceiptExtraction(null, "Boulangerie", null));
    String token = signup("ocr-partial@picsou.demo");

    mvc.perform(
            multipart("/api/ocr/receipt").file(png()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.merchant").value("Boulangerie"))
        .andExpect(jsonPath("$.total").doesNotExist());
  }

  @Test
  void unsupported_media_type_returns_400() throws Exception {
    String token = signup("ocr-badtype@picsou.demo");
    MockMultipartFile txt =
        new MockMultipartFile("file", "note.txt", "text/plain", new byte[] {1, 2});

    mvc.perform(multipart("/api/ocr/receipt").file(txt).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returns_503_when_ai_unavailable() throws Exception {
    when(ocrClient.extractReceipt(any(), any()))
        .thenThrow(new AiUnavailableException("IA non configurée"));
    String token = signup("ocr-503@picsou.demo");

    mvc.perform(
            multipart("/api/ocr/receipt").file(png()).header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable());
  }
}
