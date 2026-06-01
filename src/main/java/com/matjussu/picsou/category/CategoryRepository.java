package com.matjussu.picsou.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /** Catégories globales (user_id IS NULL) + celles de l'utilisateur. */
  List<Category> findByUserIdIsNullOrUserId(UUID userId);

  Optional<Category> findByIdAndUserId(UUID id, UUID userId);
}
