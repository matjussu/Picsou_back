package com.matjussu.picsou.transaction;

import com.matjussu.picsou.account.AccountRepository;
import com.matjussu.picsou.transaction.dto.CreateTransactionRequest;
import com.matjussu.picsou.transaction.dto.TransactionFilter;
import com.matjussu.picsou.transaction.dto.TransactionResponse;
import com.matjussu.picsou.transaction.dto.UpdateTransactionRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactions;
  private final AccountRepository accounts;
  private final TransactionMapper mapper;

  public List<TransactionResponse> search(
      UUID userId, TransactionFilter filter, Pageable pageable) {
    return transactions
        .findAll(TransactionSpecifications.withFilters(userId, filter), pageable)
        .map(mapper::toDto)
        .getContent();
  }

  public TransactionResponse create(UUID userId, CreateTransactionRequest req) {
    UUID accountId = req.accountId();
    if (accountId == null) {
      accountId =
          accounts.findByUserId(userId).stream()
              .findFirst()
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun compte"))
              .getId();
    } else if (accounts.findByIdAndUserId(accountId, userId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte inconnu");
    }
    Transaction t =
        transactions.save(
            Transaction.builder()
                .userId(userId)
                .accountId(accountId)
                .categoryId(req.categoryId())
                .amount(req.amount())
                .date(req.date())
                .description(req.description())
                .type(req.type())
                .note(req.note())
                .source(TransactionSource.manual)
                .build());
    return mapper.toDto(t);
  }

  public TransactionResponse get(UUID userId, UUID id) {
    return mapper.toDto(owned(userId, id));
  }

  public TransactionResponse update(UUID userId, UUID id, UpdateTransactionRequest req) {
    Transaction t = owned(userId, id);
    if (req.amount() != null) {
      t.setAmount(req.amount());
    }
    if (req.date() != null) {
      t.setDate(req.date());
    }
    if (req.description() != null) {
      t.setDescription(req.description());
    }
    if (req.type() != null) {
      t.setType(req.type());
    }
    if (req.categoryId() != null) {
      t.setCategoryId(req.categoryId());
    }
    if (req.note() != null) {
      t.setNote(req.note());
    }
    return mapper.toDto(transactions.save(t));
  }

  public void delete(UUID userId, UUID id) {
    transactions.delete(owned(userId, id));
  }

  private Transaction owned(UUID userId, UUID id) {
    return transactions
        .findByIdAndUserId(id, userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction inconnue"));
  }
}
