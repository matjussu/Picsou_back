package com.matjussu.picsou.ocr;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR", description = "Scan de reçu (vision Claude) → pré-remplit une transaction")
public class OcrController {

  private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

  private final OcrClient ocrClient;

  @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Extraire {total, marchand, date} d'un reçu",
      description =
          "Vision Claude via tool use. L'image est traitée en mémoire puis jetée (jamais"
              + " persistée). Pré-remplit une transaction (pas de création auto). 503 si la clé"
              + " Anthropic n'est pas configurée.")
  @ApiResponse(responseCode = "200", description = "Champs extraits (certains peuvent être nuls)")
  @ApiResponse(responseCode = "400", description = "Fichier manquant ou format non supporté")
  @ApiResponse(responseCode = "503", description = "IA non configurée / indisponible")
  public ReceiptExtraction scanReceipt(
      @AuthenticationPrincipal UUID userId, @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier manquant");
    }
    String mediaType = file.getContentType();
    if (!StringUtils.hasText(mediaType) || !ALLOWED.contains(mediaType)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Format non supporté (attendu : JPEG, PNG ou WebP)");
    }
    try {
      // Bytes en mémoire, transmis au client puis abandonnés au GC — jamais persistés.
      return ocrClient.extractReceipt(file.getBytes(), mediaType);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image illisible");
    }
  }
}
