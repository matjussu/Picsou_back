package com.matjussu.picsou.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only protected endpoint. The Phase 1 scaffolding exposes no authenticated route yet, so
 * {@link SecurityIT} needs one to assert that the JWT filter accepts access tokens but rejects
 * refresh tokens (token type confusion). Lives in test sources only.
 */
@RestController
class SecuredTestController {

  @GetMapping("/api/test/secure")
  String secure() {
    return "ok";
  }
}
