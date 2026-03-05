package com.test.financialtracker.transaction.domains.models;


/**
 * The three supported transaction types — stored as VARCHAR in DB.
 *
 * Business rules per type:
 *
 *  DEPOSIT    — money enters the system into a destination account.
 *               source_account_id is NULL (no internal source).
 *               destination_account_id is populated.
 *
 *  WITHDRAWAL — money leaves the system from a source account.
 *               source_account_id is populated.
 *               destination_account_id is NULL (no internal destination).
 *
 *  TRANSFER   — money moves between two accounts owned by the same user.
 *               Both source_account_id and destination_account_id are populated.
 *               Both accounts MUST belong to the authenticated caller.
 *               Locks acquired in consistent order to prevent deadlocks.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}