package com.matjussu.picsou.coloc.dto;

import com.matjussu.picsou.coloc.ColocRole;
import java.util.UUID;

/** Vue résumée d'un groupe pour la liste « mes groupes » (rôle = celui du user courant). */
public record GroupResponse(UUID id, String name, int memberCount, ColocRole yourRole) {}
