package com.matjussu.picsou.transaction;

import com.matjussu.picsou.transaction.dto.TransactionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

  TransactionResponse toDto(Transaction t);
}
