package com.matjussu.picsou.openbanking;

/**
 * Statut d'une connexion bancaire. Reprend exactement les labels de l'enum Postgres {@code
 * ob_status} (minuscules volontaires).
 */
public enum ObStatus {
  active,
  expired,
  revoked
}
