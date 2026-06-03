package com.matjussu.picsou.openbanking;

/**
 * Agrégateur Open Banking. Reprend exactement les labels de l'enum Postgres {@code ob_provider}
 * (minuscules volontaires). En mode mock, on utilise {@link #bridge} comme agrégateur factice.
 */
public enum ObProvider {
  bridge,
  gocardless,
  tink
}
