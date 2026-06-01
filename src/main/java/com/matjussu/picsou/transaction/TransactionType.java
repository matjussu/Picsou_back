package com.matjussu.picsou.transaction;

/** Matches the Postgres enum {@code tx_type} labels exactly (lowercase on purpose). */
public enum TransactionType {
  income,
  expense
}
