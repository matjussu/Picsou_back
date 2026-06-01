package com.matjussu.picsou.goal;

/**
 * Matches the Postgres enum {@code goal_template} labels exactly (lowercase on purpose, like
 * tx_type/account_type) so {@code @Enumerated(STRING)} maps 1:1 onto the native PG enum.
 */
public enum GoalTemplate {
  savings,
  travel,
  purchase,
  custom
}
