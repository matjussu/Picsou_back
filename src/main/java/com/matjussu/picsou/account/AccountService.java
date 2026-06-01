package com.matjussu.picsou.account;

import com.matjussu.picsou.account.dto.AccountResponse;
import com.matjussu.picsou.account.dto.CreateAccountRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accounts;

  public List<AccountResponse> list(UUID userId) {
    return accounts.findByUserId(userId).stream().map(this::toDto).toList();
  }

  public AccountResponse create(UUID userId, CreateAccountRequest req) {
    Account a =
        accounts.save(Account.builder().userId(userId).name(req.name()).type(req.type()).build());
    return toDto(a);
  }

  private AccountResponse toDto(Account a) {
    return new AccountResponse(
        a.getId(), a.getName(), a.getType(), a.getBalance(), a.getCurrency());
  }
}
