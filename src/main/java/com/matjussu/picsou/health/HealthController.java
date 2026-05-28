package com.matjussu.picsou.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Healthcheck endpoint pour monitoring")
public class HealthController {

  @GetMapping("/health")
  @Operation(summary = "Renvoie l'état de l'application")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "picsou-back");
  }
}
