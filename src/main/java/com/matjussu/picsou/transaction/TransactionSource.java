package com.matjussu.picsou.transaction;

/** Matches the Postgres enum {@code tx_source} labels exactly (lowercase on purpose). */
public enum TransactionSource {
  manual,
  ocr,
  openbanking
}
