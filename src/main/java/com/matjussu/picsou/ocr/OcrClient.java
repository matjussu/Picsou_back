package com.matjussu.picsou.ocr;

import com.matjussu.picsou.ai.AiUnavailableException;

/**
 * Abstraction du fournisseur d'OCR (vision). Seul impl prod : {@link AnthropicOcrClient}.
 * L'interface sert de seam de test (les IT la stubent via {@code @MockitoBean}, jamais l'API
 * réelle).
 */
public interface OcrClient {

  /**
   * Extrait {total, marchand, date} d'une image de reçu. L'image est traitée en mémoire et n'est
   * jamais persistée.
   *
   * @throws AiUnavailableException si l'IA n'est pas configurée (clé absente) → 503.
   */
  ReceiptExtraction extractReceipt(byte[] image, String mediaType);
}
