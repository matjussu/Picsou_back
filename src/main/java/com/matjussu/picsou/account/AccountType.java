package com.matjussu.picsou.account;

/**
 * Matches the Postgres enum {@code account_type} labels exactly.
 *
 * <p>Constants are intentionally lowercase (entorse à la convention Java UPPER_CASE) so the JPA
 * {@code @Enumerated(STRING)} name maps 1:1 onto the native PG enum labels without an {@code
 * AttributeConverter}.
 */
public enum AccountType {
  cash,
  coloc,
  bank
}
