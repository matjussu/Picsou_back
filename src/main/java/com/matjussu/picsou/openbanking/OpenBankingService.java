package com.matjussu.picsou.openbanking;

import com.matjussu.picsou.openbanking.dto.InstitutionResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service Open Banking (mock).
 *
 * <p>Pour l'instant : exposition du catalogue d'établissements. Les opérations à état (connexion,
 * synchronisation, déconnexion) sur {@code bank_connections} / {@code bank_sync_log} arrivent dans
 * une seconde passe, une fois le modèle de persistance de l'identité de banque tranché.
 */
@Service
public class OpenBankingService {

  /** Catalogue des banques connectables, dans l'ordre d'affichage. */
  public List<InstitutionResponse> institutions() {
    return BankCatalog.all().stream()
        .map(i -> new InstitutionResponse(i.slug(), i.name(), i.brandColor()))
        .toList();
  }
}
