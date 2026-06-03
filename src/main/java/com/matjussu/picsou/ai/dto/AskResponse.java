package com.matjussu.picsou.ai.dto;

/**
 * Réponse du Q&A IA libre.
 *
 * @param answer réponse FR du LLM (appuyée uniquement sur nos chiffres)
 * @param model modèle utilisé
 * @param tokensUsed coût en tokens (entrée + sortie)
 */
public record AskResponse(String answer, String model, int tokensUsed) {}
