package com.matjussu.picsou.coloc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "coloc_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColocMember {

  @Id @GeneratedValue private UUID id;

  @Column(name = "coloc_group_id")
  private UUID colocGroupId;

  @Column(name = "user_id")
  private UUID userId;

  // G1: native PG enum `coloc_role` — needs PostgreSQLEnumJdbcType (like account_type/tx_type).
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "role")
  private ColocRole role;

  @Column(name = "joined_at")
  private Instant joinedAt;

  @PrePersist
  void onCreate() {
    if (this.joinedAt == null) {
      this.joinedAt = Instant.now();
    }
    if (this.role == null) {
      this.role = ColocRole.member;
    }
  }
}
