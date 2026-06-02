package com.matjussu.picsou.coloc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.account.dto.AccountResponse;
import com.matjussu.picsou.account.dto.CreateAccountRequest;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.coloc.dto.AddMemberRequest;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest.CustomPart;
import com.matjussu.picsou.coloc.dto.CreateGroupRequest;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class SharedExpenseControllerIT {

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

  private String signup(String email) throws Exception {
    var req = new SignupRequest(email, "Strong-Password-123", email.split("@")[0]);
    var res =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(res.getResponse().getContentAsString(), AuthResponse.class).accessToken();
  }

  private UUID idOf(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private UUID createGroup(String token, String name) throws Exception {
    var body =
        mvc.perform(
                post("/api/coloc/groups")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new CreateGroupRequest(name))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asText());
  }

  private void addMember(String token, UUID gid, String email) throws Exception {
    mvc.perform(
            post("/api/coloc/groups/" + gid + "/members")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AddMemberRequest(email))))
        .andExpect(status().isCreated());
  }

  private UUID createAccount(String token) throws Exception {
    var body =
        mvc.perform(
                post("/api/accounts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            new CreateAccountRequest("Courant", AccountType.bank))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readValue(body, AccountResponse.class).id();
  }

  private org.springframework.test.web.servlet.ResultActions postExpense(
      String token, UUID gid, AddSharedExpenseRequest req) throws Exception {
    return mvc.perform(
        post("/api/coloc/groups/" + gid + "/expenses")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(req)));
  }

  @Test
  void equal_expense_created_and_listed_with_your_share() throws Exception {
    String admin = signup("sec-admin@picsou.demo");
    signup("sec-b@picsou.demo");
    signup("sec-c@picsou.demo");
    UUID gid = createGroup(admin, "Le Loft");
    addMember(admin, gid, "sec-b@picsou.demo");
    addMember(admin, gid, "sec-c@picsou.demo");
    UUID acc = createAccount(admin);
    UUID payer = idOf("sec-admin@picsou.demo");

    var req =
        new AddSharedExpenseRequest(
            payer,
            acc,
            null,
            "Courses Monoprix",
            LocalDate.parse("2026-05-10"),
            new BigDecimal("30.00"),
            SplitMethod.equal,
            List.of(idOf("sec-b@picsou.demo"), idOf("sec-c@picsou.demo")),
            null);
    postExpense(admin, gid, req)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.splitMethod").value("equal"))
        .andExpect(jsonPath("$.settled").value(false))
        .andExpect(jsonPath("$.yourShare").value(10.00));

    mvc.perform(
            get("/api/coloc/groups/" + gid + "/expenses")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].description").value("Courses Monoprix"))
        .andExpect(jsonPath("$[0].payerName").value("sec-admin"));
  }

  @Test
  void custom_sum_mismatch_returns_422() throws Exception {
    String admin = signup("sec2-admin@picsou.demo");
    signup("sec2-b@picsou.demo");
    UUID gid = createGroup(admin, "Le Loft");
    addMember(admin, gid, "sec2-b@picsou.demo");
    UUID acc = createAccount(admin);
    UUID payer = idOf("sec2-admin@picsou.demo");

    var req =
        new AddSharedExpenseRequest(
            payer,
            acc,
            null,
            "Resto",
            LocalDate.parse("2026-05-11"),
            new BigDecimal("10.00"),
            SplitMethod.custom,
            null,
            List.of(
                new CustomPart(payer, new BigDecimal("4.00")),
                new CustomPart(idOf("sec2-b@picsou.demo"), new BigDecimal("5.00"))));
    postExpense(admin, gid, req).andExpect(status().isUnprocessableEntity());
  }

  @Test
  void participant_not_member_returns_400() throws Exception {
    String admin = signup("sec3-admin@picsou.demo");
    signup("sec3-outsider@picsou.demo");
    UUID gid = createGroup(admin, "Le Loft");
    UUID acc = createAccount(admin);
    UUID payer = idOf("sec3-admin@picsou.demo");

    var req =
        new AddSharedExpenseRequest(
            payer,
            acc,
            null,
            "Courses",
            LocalDate.parse("2026-05-12"),
            new BigDecimal("10.00"),
            SplitMethod.equal,
            List.of(idOf("sec3-outsider@picsou.demo")),
            null);
    postExpense(admin, gid, req).andExpect(status().isBadRequest());
  }

  @Test
  void non_member_cannot_list_expenses() throws Exception {
    String admin = signup("sec4-admin@picsou.demo");
    UUID gid = createGroup(admin, "Le Loft");
    String outsider = signup("sec4-outsider@picsou.demo");

    mvc.perform(
            get("/api/coloc/groups/" + gid + "/expenses")
                .header("Authorization", "Bearer " + outsider))
        .andExpect(status().isNotFound());
  }

  @Test
  void expenses_ordered_by_transaction_date_desc() throws Exception {
    String admin = signup("sec5-admin@picsou.demo");
    UUID gid = createGroup(admin, "Le Loft");
    UUID acc = createAccount(admin);
    UUID payer = idOf("sec5-admin@picsou.demo");

    postExpense(
            admin,
            gid,
            new AddSharedExpenseRequest(
                payer,
                acc,
                null,
                "Ancienne",
                LocalDate.parse("2026-04-01"),
                new BigDecimal("12.00"),
                SplitMethod.equal,
                List.of(),
                null))
        .andExpect(status().isCreated());
    postExpense(
            admin,
            gid,
            new AddSharedExpenseRequest(
                payer,
                acc,
                null,
                "Récente",
                LocalDate.parse("2026-05-20"),
                new BigDecimal("8.00"),
                SplitMethod.equal,
                List.of(),
                null))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/coloc/groups/" + gid + "/expenses")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].description").value("Récente"))
        .andExpect(jsonPath("$[1].description").value("Ancienne"));
  }
}
