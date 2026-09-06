-- =====================================================================
-- V24  Record milk quantity now, add FAT / SNF later.
--
--      Milk is often weighed on the round and only tested for quality
--      afterwards. A collection can now be saved with litres alone; its
--      FAT and SNF are added later and the rate + amount are worked out
--      at that point. Such a collection sits in status PENDING_QUALITY
--      and is not eligible for a settlement run until its quality is in.
-- =====================================================================

alter table milk_collections
    alter column fat_percentage drop not null,
    alter column snf_percentage drop not null;

alter table milk_collections drop constraint milk_collections_status_check;
alter table milk_collections add constraint milk_collections_status_check
    check (status in ('DRAFT', 'CONFIRMED', 'VOID', 'PENDING_QUALITY'));
