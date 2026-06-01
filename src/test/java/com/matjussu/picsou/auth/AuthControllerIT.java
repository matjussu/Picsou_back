package com.matjussu.picsou.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.LoginRequest;
import com.matjussu.picsou.auth.dto.RefreshRequest;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.user.UserRepository;
import java.util.List;
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
class AuthControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AccountRepository accounts;
  @Autowired UserRepository users;

  @Test
  void signup_login_refresh_logout_flow() throws Exception {
    var signup = new SignupRequest("marie@picsou.demo", "Strong-Password-123", "Marie");
    var signupResult =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(signup)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andExpect(jsonPath("$.firstName").value("Marie"))
            .andReturn();
    AuthResponse signupResp =
        json.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class);

    var login = new LoginRequest("marie@picsou.demo", "Strong-Password-123");
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString());

    var refresh = new RefreshRequest(signupResp.refreshToken());
    mvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(refresh)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString());

    mvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(refresh)))
        .andExpect(status().isOk());
  }

  @Test
  void signup_with_duplicate_email_returns_409() throws Exception {
    var signup = new SignupRequest("dup@picsou.demo", "Strong-Password-123", "X");
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(signup)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(signup)))
        .andExpect(status().isConflict());
  }

  @Test
  void login_with_wrong_password_returns_401() throws Exception {
    var signup = new SignupRequest("badpw@picsou.demo", "Strong-Password-123", "X");
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(signup)))
        .andExpect(status().isCreated());
    var login = new LoginRequest("badpw@picsou.demo", "WrongPassword");
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void signup_creates_default_account() throws Exception {
    var signup = new SignupRequest("acct@picsou.demo", "Strong-Password-123", "Acct");
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(signup)))
        .andExpect(status().isCreated());

    var user = users.findByEmail("acct@picsou.demo").orElseThrow();
    List<Account> userAccounts = accounts.findByUserId(user.getId());
    assertThat(userAccounts)
        .hasSize(1)
        .first()
        .satisfies(
            a -> {
              assertThat(a.getName()).isEqualTo("Compte courant");
              assertThat(a.getType()).isEqualTo(AccountType.cash);
            });
  }
}
