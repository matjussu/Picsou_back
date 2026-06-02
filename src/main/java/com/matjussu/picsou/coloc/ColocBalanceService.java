package com.matjussu.picsou.coloc;

import com.matjussu.picsou.coloc.DebtSimplifier.Transfer;
import com.matjussu.picsou.coloc.dto.BalanceResponse;
import com.matjussu.picsou.coloc.dto.BalanceResponse.MemberBalance;
import com.matjussu.picsou.coloc.dto.TransferResponse;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ColocBalanceService {

  private final ColocMemberRepository members;
  private final SharedExpenseRepository sharedExpenses;
  private final SharedExpensePartRepository parts;
  private final UserRepository users;

  /**
   * Bilan du groupe : solde net par membre (sur parts {@code settled=false}, en ignorant la part du
   * payeur lui-même) puis virements simplifiés ({@link DebtSimplifier}).
   */
  public BalanceResponse balances(UUID currentUserId, UUID groupId) {
    List<ColocMember> groupMembers = members.findByColocGroupId(groupId);
    if (groupMembers.stream().noneMatch(m -> currentUserId.equals(m.getUserId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Groupe inconnu");
    }

    List<SharedExpense> ses = sharedExpenses.findByColocGroupId(groupId);
    Map<UUID, UUID> payerBySe =
        ses.stream().collect(Collectors.toMap(SharedExpense::getId, SharedExpense::getPayerUserId));

    Map<UUID, BigDecimal> net = new HashMap<>();
    if (!ses.isEmpty()) {
      for (SharedExpensePart p :
          parts.findBySharedExpenseIdIn(ses.stream().map(SharedExpense::getId).toList())) {
        if (p.isSettled()) {
          continue;
        }
        UUID payer = payerBySe.get(p.getSharedExpenseId());
        if (payer == null || payer.equals(p.getUserId())) {
          continue; // la part du payeur se doit à lui-même → ignorée
        }
        net.merge(p.getUserId(), p.getAmount().negate(), BigDecimal::add); // débiteur
        net.merge(payer, p.getAmount(), BigDecimal::add); // créancier
      }
    }

    List<Transfer> transfers = DebtSimplifier.simplify(net);

    Map<UUID, String> names =
        users.findAllById(groupMembers.stream().map(ColocMember::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, User::getFirstName, (a, b) -> a, HashMap::new));

    List<MemberBalance> memberBalances =
        groupMembers.stream()
            .map(
                m ->
                    new MemberBalance(
                        m.getUserId(),
                        names.get(m.getUserId()),
                        net.getOrDefault(m.getUserId(), BigDecimal.ZERO)))
            .toList();

    List<TransferResponse> transferDtos =
        transfers.stream()
            .map(
                t ->
                    new TransferResponse(
                        t.from(), names.get(t.from()), t.to(), names.get(t.to()), t.amount()))
            .toList();

    BigDecimal netToSettle =
        transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal yourNet = net.getOrDefault(currentUserId, BigDecimal.ZERO);

    return new BalanceResponse(yourNet, netToSettle, memberBalances, transferDtos);
  }
}
