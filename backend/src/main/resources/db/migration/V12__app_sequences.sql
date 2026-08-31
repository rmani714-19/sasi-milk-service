-- =====================================================================
-- V12  Application sequences for human-readable business numbers.
--      Used by the app to format producer / collection / settlement numbers;
--      uniqueness is still guaranteed by the UNIQUE columns themselves.
-- =====================================================================

create sequence producer_number_seq   start with 1001 increment by 1;
create sequence collection_number_seq start with 1     increment by 1;
create sequence settlement_number_seq start with 1     increment by 1;
