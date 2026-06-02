package com.matjussu.picsou.coloc;

import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest;
import com.matjussu.picsou.coloc.dto.AddSharedExpenseRequest.CustomPart;
import com.matjussu.picsou.coloc.dto.SharedExpenseResponse;
import com.matjussu.picsou.transaction.Transaction;
import com.matjussu.picsou.transaction.TransactionRepository;
import com.matjussu.picsou.transaction.TransactionType;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SharedExpenseService {

  private final ColocMemberRepository members;
  private final SharedExpenseRepository sharedExpenses;
  private final SharedExpensePartRepository parts;
  private final TransactionRepository transactions;
  private final UserRepository users;

  /**
   * Ajoute une dépense partagée : crée la transaction du payeur + la shared_expense + N parts. En
   * {@code equal}, le payeur a toujours une part et absorbe le reste de centimes ; en {@code
   * custom}, les parts saisies doivent sommer exactement au total (sinon 422).
   */
  @Transactional
  public SharedExpenseResponse addExpense(
      UUID currentUserId, UUID groupId, AddSharedExpenseRequest req) {
    requireMember(groupId, currentUserId);
    UUID payer = req.payerUserId();
    if (!members.existsByColocGroupIdAndUserId(groupId, payer)) {
      throw badRequest("Le payeur n'est pas membre du groupe");
    }
    BigDecimal total = money(req.total());

    Map<UUID, BigDecimal> partAmounts =
        req.splitMethod() == SplitMethod.custom
            ? customAmounts(groupId, total, req.customParts())
            : equalAmounts(groupId, total, payer, req.participantUserIds());

    Transaction tx =
        transactions.save(
            Transaction.builder()
                .userId(payer)
                .accountId(req.accountId())
                .categoryId(req.categoryId())
                .amount(total)
                .date(req.date())
                .description(req.description())
                .type(TransactionType.expense)
                .build());

    SharedExpense se =
        sharedExpenses.save(
            SharedExpense.builder()
                .transactionId(tx.getId())
                .colocGroupId(groupId)
                .payerUserId(payer)
                .totalAmount(total)
                .splitMethod(req.splitMethod())
                .build());

    List<SharedExpensePart> saved = new ArrayList<>();
    partAmounts.forEach(
        (userId, amount) ->
            saved.add(
                parts.save(
                    SharedExpensePart.builder()
                        .sharedExpenseId(se.getId())
                        .userId(userId)
                        .amount(amount)
                        .settled(false)
                        .build())));

    return toResponse(se, tx, saved, currentUserId, displayName(payer));
  }

  /**
   * Dépenses partagées du groupe, ordonnées par date de transaction (desc) — pas de created_at en
   * DB.
   */
  public List<SharedExpenseResponse> list(UUID currentUserId, UUID groupId) {
    requireMember(groupId, currentUserId);
    List<SharedExpense> ses = sharedExpenses.findByColocGroupId(groupId);
    if (ses.isEmpty()) {
      return List.of();
    }
    Map<UUID, Transaction> txById =
        transactions
            .findAllById(
                ses.stream().map(SharedExpense::getTransactionId).filter(x -> x != null).toList())
            .stream()
            .collect(Collectors.toMap(Transaction::getId, Function.identity()));
    Map<UUID, List<SharedExpensePart>> partsBySe =
        parts.findBySharedExpenseIdIn(ses.stream().map(SharedExpense::getId).toList()).stream()
            .collect(Collectors.groupingBy(SharedExpensePart::getSharedExpenseId));
    Map<UUID, String> payerNames =
        users
            .findAllById(
                ses.stream().map(SharedExpense::getPayerUserId).filter(x -> x != null).toList())
            .stream()
            .collect(Collectors.toMap(User::getId, User::getFirstName));

    return ses.stream()
        .map(
            se ->
                toResponse(
                    se,
                    txById.get(se.getTransactionId()),
                    partsBySe.getOrDefault(se.getId(), List.of()),
                    currentUserId,
                    payerNames.get(se.getPayerUserId())))
        .sorted(
            Comparator.comparing(
                    SharedExpenseResponse::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed())
        .toList();
  }

  // ── Calcul des parts ──

  private Map<UUID, BigDecimal> equalAmounts(
      UUID groupId, BigDecimal total, UUID payer, List<UUID> participantUserIds) {
    List<UUID> raw = participantUserIds == null ? List.of() : participantUserIds;
    if (new LinkedHashSet<>(raw).size() != raw.size()) {
      throw badRequest("Participant en double");
    }
    LinkedHashSet<UUID> participants = new LinkedHashSet<>(raw);
    participants.add(payer); // le payeur a toujours une part (sum(parts) == total)
    if (participants.isEmpty()) {
      throw badRequest("Aucun participant");
    }
    for (UUID p : participants) {
      if (!members.existsByColocGroupIdAndUserId(groupId, p)) {
        throw badRequest("Participant non membre du groupe : " + p);
      }
    }
    int n = participants.size();
    BigDecimal share = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
    BigDecimal remainder = total.subtract(share.multiply(BigDecimal.valueOf(n)));
    LinkedHashMap<UUID, BigDecimal> amounts = new LinkedHashMap<>();
    for (UUID p : participants) {
      amounts.put(p, share);
    }
    amounts.merge(payer, remainder, BigDecimal::add); // payeur absorbe le reste de centimes
    return amounts;
  }

  private Map<UUID, BigDecimal> customAmounts(
      UUID groupId, BigDecimal total, List<CustomPart> customParts) {
    if (customParts == null || customParts.isEmpty()) {
      throw badRequest("Parts custom manquantes");
    }
    LinkedHashMap<UUID, BigDecimal> amounts = new LinkedHashMap<>();
    BigDecimal sum = BigDecimal.ZERO;
    for (CustomPart cp : customParts) {
      if (amounts.containsKey(cp.userId())) {
        throw badRequest("Part en double pour un membre");
      }
      if (!members.existsByColocGroupIdAndUserId(groupId, cp.userId())) {
        throw badRequest("Participant non membre du groupe : " + cp.userId());
      }
      BigDecimal amount = money(cp.amount());
      amounts.put(cp.userId(), amount);
      sum = sum.add(amount);
    }
    if (sum.compareTo(total) != 0) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "La somme des parts (" + sum + ") doit être égale au total (" + total + ")");
    }
    return amounts;
  }

  // ── Helpers ──

  private SharedExpenseResponse toResponse(
      SharedExpense se,
      Transaction tx,
      List<SharedExpensePart> partList,
      UUID currentUserId,
      String payerName) {
    boolean settled =
        !partList.isEmpty() && partList.stream().allMatch(SharedExpensePart::isSettled);
    BigDecimal yourShare =
        partList.stream()
            .filter(p -> currentUserId.equals(p.getUserId()))
            .map(SharedExpensePart::getAmount)
            .findFirst()
            .orElse(null);
    return new SharedExpenseResponse(
        se.getId(),
        se.getTransactionId(),
        se.getPayerUserId(),
        payerName,
        tx != null ? tx.getDescription() : null,
        tx != null ? tx.getDate() : null,
        se.getTotalAmount(),
        se.getSplitMethod(),
        settled,
        yourShare);
  }

  private String displayName(UUID userId) {
    return users.findById(userId).map(User::getFirstName).orElse(null);
  }

  private void requireMember(UUID groupId, UUID userId) {
    if (!members.existsByColocGroupIdAndUserId(groupId, userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Groupe inconnu");
    }
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
