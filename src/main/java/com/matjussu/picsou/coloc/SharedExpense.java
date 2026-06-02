package com.matjussu.picsou.coloc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "shared_expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedExpense {

  @Id @GeneratedValue private UUID id;

  // FK plat vers transactions (cohérent avec le reste du projet — pas de @ManyToOne).
  @Column(name = "transaction_id")
  private UUID transactionId;

  @Column(name = "coloc_group_id")
  private UUID colocGroupId;

  @Column(name = "payer_user_id")
  private UUID payerUserId;

  @Column(name = "total_amount", nullable = false)
  private BigDecimal totalAmount;

  // G1: native PG enum `split_method`.
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "split_method")
  private SplitMethod splitMethod;

  @PrePersist
  void onCreate() {
    if (this.splitMethod == null) {
      this.splitMethod = SplitMethod.equal;
    }
  }
}
