package com.matjussu.picsou.goal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.goal.dto.AddContributionRequest;
import com.matjussu.picsou.goal.dto.CreateGoalRequest;
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
class GoalControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String signupAndGetToken(String email) throws Exception {
    var signup = new SignupRequest(email, "Strong-Password-123", "Goal");
    var result =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(signup)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(result.getResponse().getContentAsString(), AuthResponse.class)
        .accessToken();
  }

  private UUID createGoal(String token, String name, String target) throws Exception {
    var req =
        new CreateGoalRequest(name, new BigDecimal(target), LocalDate.parse("2026-08-01"), null);
    var body =
        mvc.perform(
                post("/api/goals")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.progressPercent").value(0))
            .andExpect(jsonPath("$.completed").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asText());
  }

  private void contribute(String token, UUID goalId, String amount) throws Exception {
    var req = new AddContributionRequest(new BigDecimal(amount), LocalDate.parse("2026-05-01"));
    mvc.perform(
            post("/api/goals/" + goalId + "/contributions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isCreated());
  }

  @Test
  void create_then_get_goal_with_progress() throws Exception {
    String token = signupAndGetToken("goal-create@picsou.demo");
    UUID id = createGoal(token, "Vacances Italie", "800");

    mvc.perform(get("/api/goals/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.goal.name").value("Vacances Italie"))
        .andExpect(jsonPath("$.goal.progressPercent").value(0))
        .andExpect(jsonPath("$.contributions.length()").value(0));
  }

  @Test
  void add_contribution_updates_current_amount_and_progress() throws Exception {
    String token = signupAndGetToken("goal-contrib@picsou.demo");
    UUID id = createGoal(token, "Épargne", "1000");
    contribute(token, id, "250");

    mvc.perform(get("/api/goals/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.goal.progressPercent").value(25))
        .andExpect(jsonPath("$.goal.completed").value(false))
        .andExpect(jsonPath("$.contributions.length()").value(1));
  }

  @Test
  void contribution_reaching_target_marks_completed() throws Exception {
    String token = signupAndGetToken("goal-complete@picsou.demo");
    UUID id = createGoal(token, "Petit objectif", "100");
    contribute(token, id, "100");

    mvc.perform(get("/api/goals/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.goal.completed").value(true))
        .andExpect(jsonPath("$.goal.progressPercent").value(100));
  }

  @Test
  void list_filters_by_status() throws Exception {
    String token = signupAndGetToken("goal-status@picsou.demo");
    UUID active = createGoal(token, "Actif", "500");
    UUID done = createGoal(token, "Atteint", "100");
    contribute(token, done, "100");

    mvc.perform(
            get("/api/goals").param("status", "active").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(active.toString()));

    mvc.perform(
            get("/api/goals")
                .param("status", "completed")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(done.toString()));
  }

  @Test
  void cannot_access_other_users_goal() throws Exception {
    String tokenB = signupAndGetToken("goal-userb@picsou.demo");
    UUID goalB = createGoal(tokenB, "Privé B", "300");

    String tokenA = signupAndGetToken("goal-usera@picsou.demo");
    mvc.perform(get("/api/goals/" + goalB).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }
}
