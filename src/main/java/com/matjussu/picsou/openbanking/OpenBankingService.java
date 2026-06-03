package com.matjussu.picsou.openbanking;

import com.matjussu.picsou.account.Account;
import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.account.AccountType;
import com.matjussu.picsou.category.Category;
import com.matjussu.picsou.category.CategoryRepository;
import com.matjussu.picsou.openbanking.MockBankTransactions.MockTx;
import com.matjussu.picsou.openbanking.dto.ConnectionResponse;
import com.matjussu.picsou.openbanking.dto.InstitutionResponse;
import com.matjussu.picsou.openbanking.dto.SyncResultResponse;
import com.matjussu.picsou.transaction.Transaction;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service Open Banking (mock).
 *
 * <p>Simule le cycle agrégateur : connexion d'une banque (OAuth factice, aucun secret réel),
 * synchronisation de transactions plausibles et déconnexion. Les données métier (connexions,
 * comptes, transactions) sont 100 % en base ; seuls les logos restent des assets front. Une
 * connexion crée un {@link Account} de type {@code bank} rattaché par {@code external_id = id de la
 * connexion}, alimenté par les transactions importées (source {@code openbanking}).
 */
@Service
@RequiredArgsConstructor
public class OpenBankingService {

  /** Agrégateur factice utilisé pour toutes les connexions mock. */
  private static final ObProvider MOCK_PROVIDER = ObProvider.bridge;

  /** Sentinelle stockée à la place d'un vrai token chiffré (aucun secret en mock). */
  private static final String MOCK_TOKEN = "MOCK";

  private final BankConnectionRepository connections;
  private final BankSyncLogRepository syncLogs;
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final CategoryRepository categories;

  /** Catalogue des banques connectables, dans l'ordre d'affichage. */
  public List<InstitutionResponse> institutions() {
    return BankCatalog.all().stream()
        .map(i -> new InstitutionResponse(i.slug(), i.name(), i.brandColor()))
        .toList();
  }

  /** Connexions de l'utilisateur (actives et révoquées) avec statut + dernière synchro. */
  public List<ConnectionResponse> listConnections(UUID userId) {
    return connections.findByUserId(userId).stream().map(this::toDto).toList();
  }

  /**
   * Connecte une banque du catalogue : crée la connexion (OAuth simulé), un compte bancaire dédié,
   * et lance une première synchronisation. Idempotent au sens métier via le garde-fou anti-doublon.
   *
   * @throws ResponseStatusException 404 si le slug est inconnu, 409 si déjà connectée (active)
   */
  @Transactional
  public ConnectionResponse connect(UUID userId, String institutionId) {
    Institution inst =
        BankCatalog.find(institutionId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Banque inconnue"));
    if (connections.existsByUserIdAndInstitutionIdAndStatus(userId, inst.slug(), ObStatus.active)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Banque déjà connectée");
    }

    BankConnection conn =
        connections.save(
            BankConnection.builder()
                .userId(userId)
                .provider(MOCK_PROVIDER)
                .institutionId(inst.slug())
                .providerUserId("mock-user")
                .accessTokenEncrypted(MOCK_TOKEN)
                .status(ObStatus.active)
                .build());

    Account account =
        accounts.save(
            Account.builder()
                .userId(userId)
                .name(inst.name())
                .type(AccountType.bank)
                .provider(inst.slug())
                .externalId(conn.getId().toString())
                .balance(MockBankTransactions.OPENING_BALANCE)
                .build());

    doSync(userId, conn, account);
    return toDto(conn);
  }

  /**
   * Synchronise une connexion : importe les nouvelles transactions mock (idempotent par {@code
   * external_id}) et met à jour le solde du compte.
   *
   * @throws ResponseStatusException 404 si la connexion n'existe pas / n'appartient pas au user,
   *     409 si elle n'est plus active
   */
  @Transactional
  public SyncResultResponse sync(UUID userId, UUID connectionId) {
    BankConnection conn =
        connections
            .findByIdAndUserId(connectionId, userId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connexion inconnue"));
    if (conn.getStatus() != ObStatus.active) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Connexion inactive");
    }
    Account account =
        accounts
            .findByUserIdAndExternalId(userId, conn.getId().toString())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Compte de la connexion introuvable"));

    BankSyncLog log = doSync(userId, conn, account);
    return new SyncResultResponse(
        conn.getId(), log.getTransactionsImported(), log.getCompletedAt());
  }

  /**
   * Déconnecte une banque : la connexion passe en {@code revoked}. Le compte et les transactions
   * déjà importées sont conservés (historique).
   *
   * @throws ResponseStatusException 404 si la connexion n'existe pas / n'appartient pas au user
   */
  @Transactional
  public void disconnect(UUID userId, UUID connectionId) {
    BankConnection conn =
        connections
            .findByIdAndUserId(connectionId, userId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connexion inconnue"));
    conn.setStatus(ObStatus.revoked);
    connections.save(conn);
  }

  // ── Interne ──

  /** Importe les transactions mock manquantes dans le compte + journalise la synchro. */
  private BankSyncLog doSync(UUID userId, BankConnection conn, Account account) {
    Map<String, UUID> cats = categoriesByName(userId);
    LocalDate today = LocalDate.now();
    BigDecimal balance = account.getBalance();
    int imported = 0;

    List<MockTx> batch = MockBankTransactions.generate(conn.getId());
    for (int seq = 0; seq < batch.size(); seq++) {
      MockTx t = batch.get(seq);
      String externalId = "mock:" + conn.getId() + ":" + seq;
      if (transactions.existsByExternalId(externalId)) {
        continue; // déjà importée — idempotent
      }
      transactions.save(
          Transaction.builder()
              .userId(userId)
              .accountId(account.getId())
              .categoryId(cats.get(t.categoryName()))
              .amount(t.amount())
              .date(today.minusDays(t.dayOffset()))
              .description(t.label())
              .type(t.type())
              .source(TransactionSource.openbanking)
              .externalId(externalId)
              .build());
      balance = balance.add(signed(t));
      imported++;
    }

    account.setBalance(balance);
    accounts.save(account);

    BankSyncLog log =
        BankSyncLog.builder()
            .bankConnectionId(conn.getId())
            .completedAt(Instant.now())
            .transactionsImported(imported)
            .build();
    return syncLogs.save(log);
  }

  private static BigDecimal signed(MockTx t) {
    return switch (t.type()) {
      case income -> t.amount();
      case expense -> t.amount().negate();
    };
  }

  private Map<String, UUID> categoriesByName(UUID userId) {
    return categories.findByUserIdIsNullOrUserId(userId).stream()
        .collect(Collectors.toMap(Category::getName, Category::getId, (a, b) -> a));
  }

  private ConnectionResponse toDto(BankConnection c) {
    Institution inst =
        BankCatalog.find(c.getInstitutionId())
            .orElseGet(
                () -> new Institution(c.getInstitutionId(), c.getInstitutionId(), "#888888"));
    Account account =
        accounts.findByUserIdAndExternalId(c.getUserId(), c.getId().toString()).orElse(null);
    Instant lastSyncAt =
        syncLogs
            .findFirstByBankConnectionIdOrderByStartedAtDesc(c.getId())
            .map(BankSyncLog::getCompletedAt)
            .orElse(null);
    long txCount = account == null ? 0 : transactions.countByAccountId(account.getId());
    return new ConnectionResponse(
        c.getId(),
        inst.slug(),
        inst.name(),
        inst.brandColor(),
        c.getStatus().name(),
        account == null ? null : account.getId(),
        txCount,
        lastSyncAt,
        c.getCreatedAt());
  }
}
