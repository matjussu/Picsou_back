package com.matjussu.picsou.coloc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedExpensePartRepository extends JpaRepository<SharedExpensePart, UUID> {

  List<SharedExpensePart> findBySharedExpenseId(UUID sharedExpenseId);

  List<SharedExpensePart> findBySharedExpenseIdIn(List<UUID> sharedExpenseIds);
}
