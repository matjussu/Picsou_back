package com.matjussu.picsou.openbanking;

import com.matjussu.picsou.openbanking.dto.InstitutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/openbanking")
@RequiredArgsConstructor
@Tag(name = "Open Banking", description = "Connexion de comptes bancaires (mock, données simulées)")
public class OpenBankingController {

  private final OpenBankingService service;

  @GetMapping("/institutions")
  @Operation(summary = "Lister les banques connectables (catalogue mock)")
  @ApiResponse(responseCode = "200", description = "Catalogue des banques")
  public List<InstitutionResponse> institutions() {
    return service.institutions();
  }
}
