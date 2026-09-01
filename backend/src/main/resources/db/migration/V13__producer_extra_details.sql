-- =====================================================================
-- V13  Producer: alternate mobile, UPI id, Aadhaar (encrypted like bank no.)
-- =====================================================================

alter table producers
    add column alternate_mobile text,
    add column upi_id           text,
    add column aadhaar_enc      text,        -- app-layer AES-GCM encrypted
    add column aadhaar_last4    text,        -- for masked display
    add column aadhaar_hash     text;        -- deterministic HMAC for dedupe / lookup

create index ix_producers_aadhaar_hash on producers(aadhaar_hash) where aadhaar_hash is not null;
