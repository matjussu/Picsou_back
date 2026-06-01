package com.matjussu.picsou.transaction;

import com.matjussu.picsou.transaction.dto.TransactionFilter;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builder de filtres transactions — 100% backend (critère noté du projet).
 *
 * <p>Le scope utilisateur est TOUJOURS appliqué : une Specification ne renvoie jamais les
 * transactions d'un autre user, quels que soient les autres filtres.
 */
public final class TransactionSpecifications {

  private TransactionSpecifications() {}

  public static Specification<Transaction> withFilters(UUID userId, TransactionFilter f) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userId"), userId)); // user-scope toujours

      if (f.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("date"), f.from()));
      }
      if (f.to() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("date"), f.to()));
      }
      if (f.categoryId() != null) {
        predicates.add(cb.equal(root.get("categoryId"), f.categoryId()));
      }
      if (f.accountId() != null) {
        predicates.add(cb.equal(root.get("accountId"), f.accountId()));
      }
      if (f.minAmount() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), f.minAmount()));
      }
      if (f.maxAmount() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), f.maxAmount()));
      }
      if (f.type() != null) {
        predicates.add(cb.equal(root.get("type"), f.type())); // G2: binding via @JdbcType du champ
      }
      if (f.q() != null && !f.q().isBlank()) {
        String like = "%" + f.q().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.<String>get("description")), like),
                cb.like(cb.lower(root.<String>get("note")), like)));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
