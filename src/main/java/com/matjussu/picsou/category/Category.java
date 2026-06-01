package com.matjussu.picsou.category;

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
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

  @Id @GeneratedValue private UUID id;

  // NULL = catégorie globale par défaut (seedée en V2) ; sinon = catégorie perso de l'utilisateur.
  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private String name;

  @Column(name = "icon_key")
  private String iconKey;

  @Column(name = "color_key")
  private String colorKey;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  // Self-référence (sous-catégorie) en UUID plat — pas de @ManyToOne.
  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }
}
