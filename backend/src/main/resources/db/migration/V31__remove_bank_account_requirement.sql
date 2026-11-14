-- V31  Bank account details are no longer kept in the application.
--
-- Payments are made by the owner through the bank; the app only records the bank submission and the bank
-- reference. A payment line therefore no longer points at a producer bank account. The old account tables and
-- columns (producer_bank_accounts, center_bank_accounts, drivers.account_*, payment_bank_balance_checks) are
-- left untouched so no stored data is lost, but nothing reads or writes them any more.
alter table payouts alter column bank_account_id drop not null;
