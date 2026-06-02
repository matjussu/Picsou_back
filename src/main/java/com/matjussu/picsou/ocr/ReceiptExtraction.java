package com.matjussu.picsou.ocr;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Champs extraits d'un reçu (vision Claude). Tous nullables : une extraction partielle (un champ
 * non trouvé) reste valide — le front pré-remplit ce qu'il peut, l'utilisateur complète.
 */
public record ReceiptExtraction(BigDecimal total, String merchant, LocalDate date) {}
