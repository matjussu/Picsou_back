package com.matjussu.picsou.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.auth.jwt.JwtTokenProvider;
import com.matjussu.picsou.coloc.ColocGroup;
import com.matjussu.picsou.coloc.ColocGroupRepository;
import com.matjussu.picsou.coloc.ColocMember;
import com.matjussu.picsou.coloc.ColocMemberRepository;
import com.matjussu.picsou.coloc.ColocRole;
import com.matjussu.picsou.coloc.SharedExpenseService;
import com.matjussu.picsou.coloc.SplitMethod;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest;
import com.matjussu.picsou.coloc.dto.ColocEvent;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sécurité + diffusion du canal STOMP coloc : CONNECT JWT, SUBSCRIBE membre, broadcast afterCommit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class WebSocketColocIT {

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @LocalServerPort int port;

  @Autowired JwtTokenProvider jwt;
  @Autowired UserRepository users;
  @Autowired AccountRepository accounts;
  @Autowired ColocGroupRepository groups;
  @Autowired ColocMemberRepository members;
  @Autowired SharedExpenseService expenses;

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

  private WebSocketStompClient newClient() {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(new ObjectMapper().findAndRegisterModules());
    client.setMessageConverter(converter);
    return client;
  }

  private CompletableFuture<StompSession> connect(WebSocketStompClient client, String bearer) {
    StompHeaders connectHeaders = new StompHeaders();
    if (bearer != null) {
      connectHeaders.add("Authorization", "Bearer " + bearer);
    }
    return client.connectAsync(
        "ws://localhost:" + port + "/ws",
        new WebSocketHttpHeaders(),
        connectHeaders,
        new StompSessionHandlerAdapter() {});
  }

  private StompFrameHandler collectInto(BlockingQueue<ColocEvent> queue) {
    return new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return ColocEvent.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        queue.add((ColocEvent) payload);
      }
    };
  }

  private void addExpense(UUID payer, UUID gid, List<UUID> participants) {
    expenses.addExpense(
        payer,
        gid,
        new AddSharedExpenseRequest(
            payer,
            account(payer),
            null,
            "Courses",
            LocalDate.parse("2026-05-10"),
            new BigDecimal("20.00"),
            SplitMethod.equal,
            participants,
            null));
  }

  @Test
  void member_subscribes_and_receives_broadcast() throws Exception {
    UUID a = user("ws-a@picsou.demo");
    UUID b = user("ws-b@picsou.demo");
    UUID gid = group(a, b);

    StompSession session =
        connect(newClient(), jwt.generateAccessToken(a)).get(5, TimeUnit.SECONDS);
    BlockingQueue<ColocEvent> received = new LinkedBlockingQueue<>();
    session.subscribe("/topic/coloc/" + gid, collectInto(received));
    Thread.sleep(500); // laisse le SUBSCRIBE s'enregistrer

    addExpense(a, gid, List.of(b));

    ColocEvent event = received.poll(5, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat(event.type()).isEqualTo("expense.added");
    assertThat(event.amount()).isEqualByComparingTo("20.00");
  }

  @Test
  void connect_without_jwt_is_rejected() {
    CompletableFuture<StompSession> future = connect(newClient(), null);
    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
  }

  @Test
  void non_member_does_not_receive_broadcast() throws Exception {
    UUID a = user("ws-owner@picsou.demo");
    UUID b = user("ws-member@picsou.demo");
    UUID outsider = user("ws-outsider@picsou.demo");
    UUID gid = group(a, b);

    StompSession session =
        connect(newClient(), jwt.generateAccessToken(outsider)).get(5, TimeUnit.SECONDS);
    BlockingQueue<ColocEvent> received = new LinkedBlockingQueue<>();
    try {
      session.subscribe("/topic/coloc/" + gid, collectInto(received)); // rejeté (non membre)
    } catch (Exception ignored) {
      // selon le timing, l'exception peut survenir ici ou en async (ERROR frame)
    }
    Thread.sleep(500);

    addExpense(a, gid, List.of(b));

    assertThat(received.poll(2, TimeUnit.SECONDS)).isNull(); // non-membre ne reçoit rien
  }
}
