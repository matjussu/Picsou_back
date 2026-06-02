package com.matjussu.picsou.coloc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.auth.dto.AuthResponse;
import com.matjussu.picsou.auth.dto.SignupRequest;
import com.matjussu.picsou.coloc.dto.AddMemberRequest;
import com.matjussu.picsou.coloc.dto.CreateGroupRequest;
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
class ColocGroupControllerIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String signup(String email) throws Exception {
    var req = new SignupRequest(email, "Strong-Password-123", "Coloc");
    var result =
        mvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(result.getResponse().getContentAsString(), AuthResponse.class)
        .accessToken();
  }

  private UUID createGroup(String token, String name) throws Exception {
    var body =
        mvc.perform(
                post("/api/coloc/groups")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new CreateGroupRequest(name))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.yourRole").value("admin"))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asText());
  }

  private org.springframework.test.web.servlet.ResultActions addMember(
      String token, UUID groupId, String email) throws Exception {
    return mvc.perform(
        post("/api/coloc/groups/" + groupId + "/members")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new AddMemberRequest(email))));
  }

  @Test
  void create_group_makes_creator_admin_and_lists_it() throws Exception {
    String token = signup("coloc-create@picsou.demo");
    UUID id = createGroup(token, "Le Loft");

    mvc.perform(get("/api/coloc/groups").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].name").value("Le Loft"))
        .andExpect(jsonPath("$[0].yourRole").value("admin"));
  }

  @Test
  void admin_adds_member_then_detail_lists_both() throws Exception {
    String admin = signup("coloc-admin@picsou.demo");
    signup("coloc-titouan@picsou.demo");
    UUID id = createGroup(admin, "Le Loft");

    addMember(admin, id, "coloc-titouan@picsou.demo")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("member"))
        .andExpect(jsonPath("$.email").value("coloc-titouan@picsou.demo"));

    mvc.perform(get("/api/coloc/groups/" + id).header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Le Loft"))
        .andExpect(jsonPath("$.members.length()").value(2));
  }

  @Test
  void non_member_cannot_see_group_detail() throws Exception {
    String owner = signup("coloc-owner@picsou.demo");
    UUID id = createGroup(owner, "Privé");

    String outsider = signup("coloc-outsider@picsou.demo");
    mvc.perform(get("/api/coloc/groups/" + id).header("Authorization", "Bearer " + outsider))
        .andExpect(status().isNotFound());
  }

  @Test
  void non_admin_member_cannot_add_member() throws Exception {
    String admin = signup("coloc-a@picsou.demo");
    signup("coloc-b@picsou.demo");
    signup("coloc-c@picsou.demo");
    UUID id = createGroup(admin, "Le Loft");
    addMember(admin, id, "coloc-b@picsou.demo").andExpect(status().isCreated());

    String memberB = signupExisting("coloc-b@picsou.demo");
    addMember(memberB, id, "coloc-c@picsou.demo").andExpect(status().isForbidden());
  }

  @Test
  void adding_unknown_email_returns_404() throws Exception {
    String admin = signup("coloc-admin2@picsou.demo");
    UUID id = createGroup(admin, "Le Loft");
    addMember(admin, id, "ghost@picsou.demo").andExpect(status().isNotFound());
  }

  @Test
  void adding_duplicate_member_returns_409() throws Exception {
    String admin = signup("coloc-admin3@picsou.demo");
    signup("coloc-dup@picsou.demo");
    UUID id = createGroup(admin, "Le Loft");
    addMember(admin, id, "coloc-dup@picsou.demo").andExpect(status().isCreated());
    addMember(admin, id, "coloc-dup@picsou.demo").andExpect(status().isConflict());
  }

  /** Re-login d'un user déjà inscrit (pour récupérer un token d'un membre non-admin). */
  private String signupExisting(String email) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            new com.matjussu.picsou.auth.dto.LoginRequest(
                                email, "Strong-Password-123"))))
            .andExpect(status().isOk())
            .andReturn();
    return json.readValue(result.getResponse().getContentAsString(), AuthResponse.class)
        .accessToken();
  }
}
