package com.matjussu.picsou.coloc;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColocGroupRepository extends JpaRepository<ColocGroup, UUID> {}
