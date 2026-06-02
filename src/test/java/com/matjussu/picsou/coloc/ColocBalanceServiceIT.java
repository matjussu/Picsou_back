package com.matjussu.picsou.coloc;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest;
import com.matjussu.picsou.coloc.dto.BalanceResponse;
import com.matjussu.picsou.coloc.dto.SharedExpenseResponse;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Soldes (skip part du payeur, parts settled exclues) + simplify + settle (par dépense / all). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ColocBalanceServiceIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired SharedExpenseService expenses;
  @Autowired ColocBalanceService balances;
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

  private SharedExpenseResponse equalExpense(
      UUID payer, UUID gid, String total, List<UUID> participants) {
    return expenses.addExpense(
        payer,
        gid,
        new AddSharedExpenseRequest(
            payer,
            account(payer),
            null,
            "Dépense",
            LocalDate.parse("2026-05-10"),
            new BigDecimal(total),
            SplitMethod.equal,
            participants,
            null));
  }

  @Test
  void balances_simplify_to_two_transfers() {
    UUID a = user("bal-a@picsou.demo");
    UUID b = user("bal-b@picsou.demo");
    UUID c = user("bal-c@picsou.demo");
    UUID gid = group(a, b, c);

    equalExpense(a, gid, "30.00", List.of(b, c)); // A +20, B -10, C -10
    equalExpense(b, gid, "30.00", List.of(a, c)); // B +20, A -10, C -10
    // net : A +10, B +10, C -20

    BalanceResponse asC = balances.balances(c, gid);
    assertThat(asC.yourNet()).isEqualByComparingTo("-20.00");
    assertThat(asC.netToSettle()).isEqualByComparingTo("20.00");
    assertThat(asC.transfers()).hasSize(2);
    assertThat(asC.transfers()).allSatisfy(t -> assertThat(t.fromUserId()).isEqualTo(c));

    BalanceResponse asA = balances.balances(a, gid);
    assertThat(asA.yourNet()).isEqualByComparingTo("10.00");
  }

  @Test
  void settle_all_zeroes_balances() {
    UUID a = user("set-a@picsou.demo");
    UUID b = user("set-b@picsou.demo");
    UUID gid = group(a, b);
    equalExpense(a, gid, "20.00", List.of(b)); // A +10, B -10

    expenses.settleAll(a, gid);

    BalanceResponse after = balances.balances(a, gid);
    assertThat(after.transfers()).isEmpty();
    assertThat(after.netToSettle()).isEqualByComparingTo("0.00");
    assertThat(after.yourNet()).isEqualByComparingTo("0.00");
  }

  @Test
  void settle_one_expense_excludes_it_from_balances() {
    UUID a = user("setone-a@picsou.demo");
    UUID b = user("setone-b@picsou.demo");
    UUID gid = group(a, b);
    SharedExpenseResponse e1 = equalExpense(a, gid, "20.00", List.of(b)); // A +10, B -10
    equalExpense(a, gid, "10.00", List.of(b)); // A +5, B -5

    expenses.settleExpense(a, e1.id()); // règle seulement la 1re

    BalanceResponse after = balances.balances(a, gid);
    assertThat(after.yourNet()).isEqualByComparingTo("5.00"); // reste la 2e dépense
    assertThat(after.transfers()).hasSize(1);
  }
}
