package com.matjussu.picsou.ai;

import java.math.BigDecimal;

/** Part d'une catégorie dans les dépenses du mois (chiffre calculé par NOUS, pas par le LLM). */
public record CategoryShare(String name, BigDecimal amount) {}
