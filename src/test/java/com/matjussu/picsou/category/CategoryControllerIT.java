package com.matjussu.picsou.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.category.dto.CreateCategoryRequest;
import com.matjussu.picsou.transaction.Transaction;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CategoryControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired TransactionRepository transactions;

  private String token;
  private UUID userId;

  private void authenticate(String email) throws Exception {
    var signup = new SignupRequest(email, "Strong-Password-123", "Cat");
    var result =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(signup)))
            .andExpect(status().isCreated())
            .andReturn();
    token =
        json.readValue(result.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    userId = users.findByEmail(email).orElseThrow().getId();
  }

  private JsonNode listCategories() throws Exception {
    var body =
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private UUID createCustom(String name) throws Exception {
    var req = new CreateCategoryRequest(name, "tag", "blue", null);
    var body =
        mvc.perform(
                post("/api/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asText());
  }

  @Test
  void list_returns_default_categories_with_flag() throws Exception {
    authenticate("cat-list@picsou.demo");
    JsonNode arr = listCategories();
    org.assertj.core.api.Assertions.assertThat(arr.size()).isGreaterThanOrEqualTo(15);
    // Les catégories seedées (V2) sont globales → isDefault doit être true (anti-régression
    // MapStruct).
    org.assertj.core.api.Assertions.assertThat(arr.get(0).get("isDefault").asBoolean()).isTrue();
  }

  @Test
  void create_then_delete_custom_category() throws Exception {
    authenticate("cat-crud@picsou.demo");
    UUID id = createCustom("Voyages");
    mvc.perform(delete("/api/categories/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void delete_default_category_returns_409() throws Exception {
    authenticate("cat-del-default@picsou.demo");
    JsonNode arr = listCategories();
    UUID defaultId = UUID.fromString(arr.get(0).get("id").asText());
    mvc.perform(delete("/api/categories/" + defaultId).header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void delete_referenced_category_returns_409() throws Exception {
    authenticate("cat-del-ref@picsou.demo");
    UUID id = createCustom("Abonnements");
    transactions.save(
        Transaction.builder()
            .userId(userId)
            .categoryId(id)
            .amount(new BigDecimal("9.99"))
            .date(LocalDate.parse("2026-05-01"))
            .description("Netflix")
            .type(TransactionType.expense)
            .build());
    mvc.perform(delete("/api/categories/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }
}
