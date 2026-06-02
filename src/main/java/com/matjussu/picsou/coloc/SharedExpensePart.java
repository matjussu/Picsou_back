package com.matjussu.picsou.coloc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shared_expense_parts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedExpensePart {

  @Id @GeneratedValue private UUID id;

  @Column(name = "shared_expense_id")
  private UUID sharedExpenseId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private boolean settled;

  @Column(name = "settled_at")
  private Instant settledAt;
}
