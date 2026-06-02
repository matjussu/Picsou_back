package com.matjussu.picsou.coloc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "coloc_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColocGroup {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }
}
