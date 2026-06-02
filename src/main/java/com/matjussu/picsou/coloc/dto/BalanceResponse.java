package com.matjussu.picsou.coloc.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bilan d'un groupe orienté user courant.
 *
 * <ul>
 *   <li>{@code yourNet} : solde du user courant (&gt;0 on lui doit, &lt;0 il doit).
 *   <li>{@code netToSettle} : montant total à faire circuler (somme des virements simplifiés).
 *   <li>{@code balances} : solde net signé par membre.
 *   <li>{@code transfers} : virements simplifiés suggérés (présentationnel — cf settle-all).
 * </ul>
 */
public record BalanceResponse(
    BigDecimal yourNet,
    BigDecimal netToSettle,
    List<MemberBalance> balances,
    List<TransferResponse> transfers) {

  public record MemberBalance(UUID userId, String name, BigDecimal net) {}
}
