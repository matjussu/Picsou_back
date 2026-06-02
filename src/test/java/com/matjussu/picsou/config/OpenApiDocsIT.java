package com.matjussu.picsou.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke automatisé du critère noté "Swagger exhaustif" : vérifie que l'OpenAPI doc expose les 3
 * tags métier et que le 409 du DELETE catégorie est documenté. Déterministe et CI-safe (pas besoin
 * de la connexion Supabase d'un spring-boot:run manuel).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OpenApiDocsIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;

  @Test
  void api_docs_expose_all_business_tags_and_endpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Transactions")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Categories")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Accounts")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Goals")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Dashboard")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Coloc")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Predictions")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Insights")))
        .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("OCR")))
        .andExpect(jsonPath("$.paths['/api/transactions'].get").exists())
        .andExpect(jsonPath("$.paths['/api/transactions'].post").exists())
        .andExpect(jsonPath("$.paths['/api/categories'].get").exists())
        .andExpect(jsonPath("$.paths['/api/accounts'].get").exists())
        .andExpect(jsonPath("$.paths['/api/goals'].get").exists())
        .andExpect(jsonPath("$.paths['/api/goals/{id}/contributions'].post").exists())
        .andExpect(jsonPath("$.paths['/api/dashboard/summary'].get").exists())
        .andExpect(jsonPath("$.paths['/api/dashboard/charts/monthly'].get").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups'].get").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups'].post").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups/{id}/members'].post").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups/{groupId}/expenses'].get").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups/{groupId}/expenses'].post").exists())
        // Le 422 (split custom incohérent) doit être documenté sur l'ajout de dépense partagée.
        .andExpect(
            jsonPath("$.paths['/api/coloc/groups/{groupId}/expenses'].post.responses.422").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups/{groupId}/balances'].get").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/groups/{groupId}/settle-all'].post").exists())
        .andExpect(jsonPath("$.paths['/api/coloc/expenses/{expenseId}/settle'].post").exists())
        .andExpect(jsonPath("$.paths['/api/predictions/end-of-month'].get").exists())
        .andExpect(jsonPath("$.paths['/api/insights/monthly'].post").exists())
        .andExpect(jsonPath("$.paths['/api/insights/monthly'].post.responses.503").exists())
        .andExpect(jsonPath("$.paths['/api/ocr/receipt'].post").exists())
        .andExpect(jsonPath("$.paths['/api/ocr/receipt'].post.responses.503").exists())
        // Le 409 (catégorie par défaut/référencée) doit être documenté sur le DELETE.
        .andExpect(jsonPath("$.paths['/api/categories/{id}'].delete.responses.409").exists())
        // Le 409 (membre déjà présent) doit être documenté sur l'ajout de membre coloc.
        .andExpect(
            jsonPath("$.paths['/api/coloc/groups/{id}/members'].post.responses.409").exists());
  }
}
