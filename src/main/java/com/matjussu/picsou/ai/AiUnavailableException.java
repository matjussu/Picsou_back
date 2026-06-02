package com.matjussu.picsou.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * IA non configurée (clé Anthropic absente) ou indisponible → 503 propre (pas de fallback simulé).
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AiUnavailableException extends RuntimeException {
  public AiUnavailableException(String message) {
    super(message);
  }
}
