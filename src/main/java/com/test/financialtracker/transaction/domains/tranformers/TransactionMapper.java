package com.test.financialtracker.transaction.domains.tranformers;


import com.test.financialtracker.transaction.domains.entity.Transactions;
import com.test.financialtracker.transaction.domains.models.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public Transaction toDomain(Transactions entity) {
        return Transaction.builder()
                .id(entity.getId())
                .sourceAccountId(entity.getSourceAccountId())
                .destinationAccountId(entity.getDestinationAccountId())
                .type(entity.getType())
                .amount(entity.getAmount())
                .timestamp(entity.getTimestamp())
                .referenceId(entity.getReferenceId())
                .build();
    }

    public Transactions toEntity(Transaction domain) {
        return Transactions.builder()
                .id(domain.getId())
                .sourceAccountId(domain.getSourceAccountId())
                .destinationAccountId(domain.getDestinationAccountId())
                .type(domain.getType())
                .amount(domain.getAmount())
                .timestamp(domain.getTimestamp())
                .referenceId(domain.getReferenceId())
                .build();
    }
}