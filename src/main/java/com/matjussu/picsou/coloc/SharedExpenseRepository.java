package com.matjussu.picsou.coloc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedExpenseRepository extends JpaRepository<SharedExpense, UUID> {

  List<SharedExpense> findByColocGroupId(UUID colocGroupId);
}
