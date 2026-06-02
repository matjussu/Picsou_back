package com.matjussu.picsou.coloc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest.CustomPart;
import com.matjussu.picsou.coloc.dto.SharedExpenseResponse;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Vérifie la règle des centimes (equal) et la validation custom au niveau service (parts en DB).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SharedExpenseServiceIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired SharedExpenseService service;
  @Autowired SharedExpensePartRepository parts;
  @Autowired UserRepository users;
  @Autowired AccountRepository accounts;
  @Autowired ColocGroupRepository groups;
  @Autowired ColocMemberRepository members;

  private UUID user(String email) {
    return users
        .save(User.builder().email(email).passwordHash("x").firstName(email.split("@")[0]).build())
        .getId();
  }

  private UUID group(UUID admin, UUID... others) {
    UUID gid =
        groups.save(ColocGroup.builder().name("Le Loft").createdByUserId(admin).build()).getId();
    members.save(
        ColocMember.builder().colocGroupId(gid).userId(admin).role(ColocRole.admin).build());
    for (UUID u : others) {
      members.save(
          ColocMember.builder().colocGroupId(gid).userId(u).role(ColocRole.member).build());
    }
    return gid;
  }

  private UUID account(UUID owner) {
    return accounts
        .save(Account.builder().userId(owner).name("Courant").type(AccountType.bank).build())
        .getId();
  }

  private Map<UUID, BigDecimal> partsByUser(UUID sharedExpenseId) {
    return parts.findBySharedExpenseId(sharedExpenseId).stream()
        .collect(Collectors.toMap(SharedExpensePart::getUserId, SharedExpensePart::getAmount));
  }

  @Test
  void equalSplit_distributesCentRemainderToPayer() {
    UUID payer = user("se-payer@picsou.demo");
    UUID b = user("se-b@picsou.demo");
    UUID c = user("se-c@picsou.demo");
    UUID gid = group(payer, b, c);
    UUID acc = account(payer);

    var req =
        new AddSharedExpenseRequest(
            payer,
            acc,
            null,
            "Courses",
            LocalDate.parse("2026-05-10"),
            new BigDecimal("10.00"),
            SplitMethod.equal,
            List.of(b, c), // payeur auto-ajouté → 3 participants
            null);
    SharedExpenseResponse resp = service.addExpense(payer, gid, req);

    Map<UUID, BigDecimal> p = partsByUser(resp.id());
    assertThat(p).hasSize(3);
    assertThat(p.get(payer)).isEqualByComparingTo("3.34"); // absorbe le centime
    assertThat(p.get(b)).isEqualByComparingTo("3.33");
    assertThat(p.get(c)).isEqualByComparingTo("3.33");
    assertThat(p.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("10.00");
  }

  @Test
  void customSplit_sumMismatch_throws422() {
    UUID payer = user("se-cm-payer@picsou.demo");
    UUID b = user("se-cm-b@picsou.demo");
    UUID gid = group(payer, b);
    UUID acc = account(payer);

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
                new CustomPart(b, new BigDecimal("5.00"))));

    assertThatThrownBy(() -> service.addExpense(payer, gid, req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("422");
  }

  @Test
  void customSplit_ok_persistsExactParts() {
    UUID payer = user("se-co-payer@picsou.demo");
    UUID b = user("se-co-b@picsou.demo");
    UUID gid = group(payer, b);
    UUID acc = account(payer);

    var req =
        new AddSharedExpenseRequest(
            payer,
            acc,
            null,
            "Resto",
            LocalDate.parse("2026-05-12"),
            new BigDecimal("10.00"),
            SplitMethod.custom,
            null,
            List.of(
                new CustomPart(payer, new BigDecimal("6.50")),
                new CustomPart(b, new BigDecimal("3.50"))));
    SharedExpenseResponse resp = service.addExpense(payer, gid, req);

    Map<UUID, BigDecimal> p = partsByUser(resp.id());
    assertThat(p.get(payer)).isEqualByComparingTo("6.50");
    assertThat(p.get(b)).isEqualByComparingTo("3.50");
  }
}
