package com.matjussu.picsou.prediction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prediction {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "forecast_date", nullable = false)
  private LocalDate forecastDate;

  @Column(name = "predicted_balance")
  private BigDecimal predictedBalance;

  // Colonne JSONB native PG → mappée via @JdbcTypeCode(JSON) (Hibernate 6 + Jackson).
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "anomalies", columnDefinition = "jsonb")
  private List<Anomaly> anomalies;

  @Column(name = "computed_at")
  private Instant computedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    this.computedAt = Instant.now();
  }
}
