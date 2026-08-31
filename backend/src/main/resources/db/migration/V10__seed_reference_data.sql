-- =====================================================================
-- V10  Seed: structural / reference data only.
--      NO producers, NO rate charts, NO incentives (that is business data).
-- =====================================================================

-- ---------------------------------------------------------------------
-- roles + permission codes (from spec user_roles)
-- ---------------------------------------------------------------------
insert into roles (code, name, description, permissions, is_system) values
 ('ADMIN', 'Administrator', 'Full system access',
   '["ALL"]'::jsonb, true),
 ('CENTER_MANAGER', 'Center Manager', 'Runs the collection center',
   '["VIEW_DASHBOARD","MANAGE_PRODUCERS","MANAGE_RATES","MANAGE_INCENTIVES","MANAGE_DEDUCTIONS","MANAGE_ADVANCES","VIEW_COLLECTIONS","REVIEW_SETTLEMENT","APPROVE_SETTLEMENT","VIEW_REPORTS","VIEW_PAYMENTS"]'::jsonb, true),
 ('COLLECTION_OPERATOR', 'Collection Operator', 'Records daily milk collection',
   '["VIEW_PRODUCERS","CREATE_COLLECTION","VIEW_COLLECTION","PRINT_RECEIPT","VIEW_BASIC_REPORTS"]'::jsonb, true),
 ('ACCOUNTS_USER', 'Accounts User', 'Deductions, advances, settlement, reports',
   '["VIEW_COLLECTIONS","MANAGE_DEDUCTIONS","MANAGE_ADVANCES","GENERATE_SETTLEMENT","REVIEW_SETTLEMENT","VIEW_PAYMENTS","GENERATE_REPORTS"]'::jsonb, true),
 ('REPORT_VIEWER', 'Report Viewer', 'Read-only dashboards and reports',
   '["VIEW_DASHBOARD","VIEW_REPORTS","EXPORT_REPORTS"]'::jsonb, true);

-- ---------------------------------------------------------------------
-- shifts
-- ---------------------------------------------------------------------
insert into shifts (code, name, sort_order, status) values
 ('MORNING', 'Morning', 1, 'ACTIVE'),
 ('EVENING', 'Evening', 2, 'ACTIVE');

-- ---------------------------------------------------------------------
-- regions (four operational regions per spec)
-- ---------------------------------------------------------------------
insert into regions (code, name, status) values
 ('REGION_1', 'Region 1', 'ACTIVE'),
 ('REGION_2', 'Region 2', 'ACTIVE'),
 ('REGION_3', 'Region 3', 'ACTIVE'),
 ('REGION_4', 'Region 4', 'ACTIVE');

-- ---------------------------------------------------------------------
-- deduction types
-- ---------------------------------------------------------------------
-- client: deductions are cow feed + emergency cash; advance recovery has its own ledger.
insert into deduction_types (code, name, is_system, affects_ledger) values
 ('FEED',       'Cow Feed',   true, false),
 ('VETERINARY', 'Veterinary', true, false),
 ('EMERGENCY',  'Emergency',  true, false),
 ('OTHER',      'Other',      true, false);

-- ---------------------------------------------------------------------
-- center: one row with safe defaults. Details filled in via "My Center".
-- NOTE: no rate chart is seeded — an ADMIN must create a COMMON rate_charts
-- row (ts_value) before the first collection can be priced.
-- ---------------------------------------------------------------------
insert into center (center_name, currency_code, timezone, settlement_day_of_week,
                    rounding_mode, rounding_scale, incentive_stacking, incentive_max_per_litre,
                    allow_missing_rate, fat_min, fat_max, snf_min, snf_max)
values ('SASI MILK SERVICE', 'INR', 'Asia/Kolkata', 1,
        'HALF_EVEN', 0, 'ADD', 2.0000,
        false, 2.00, 14.00, 6.00, 12.00);

-- ---------------------------------------------------------------------
-- notification channels (all disabled until providers are configured)
-- ---------------------------------------------------------------------
insert into notification_channel_config (channel, enabled) values
 ('SMS', false), ('WHATSAPP', false), ('EMAIL', false);

-- ---------------------------------------------------------------------
-- notification templates: seeded from spec text; editable by ADMIN.
-- ---------------------------------------------------------------------
insert into notification_templates (code, channel, locale, body) values
 ('PAYMENT_SUCCESS', 'SMS', 'en',
  'SASI MILK SERVICE: Weekly payment processed. Producer: {{producerName}}, Period: {{period}}, Total Milk: {{totalLitres}} L, Milk Amount: Rs.{{milkAmount}}, Incentives: Rs.{{incentiveAmount}}, Deductions: Rs.{{deductionAmount}}, Net Payment: Rs.{{netAmount}}, Reference: {{paymentReference}}.'),
 ('PAYMENT_FAILED', 'SMS', 'en',
  'SASI MILK SERVICE: Your weekly producer payment could not be completed. Please contact the milk collection center.'),
 ('COLLECTION_CREATED', 'SMS', 'en',
  'SASI MILK SERVICE: Milk collection recorded for {{producerName}}. Shift: {{shift}}, Milk: {{litres}} L, Amount: Rs.{{amount}}, Collection No: {{collectionNumber}}.');
