-- =====================================================================
-- V14  Enrich the COLLECTION_CREATED SMS: litres, rate/litre, amounts.
--      Editable afterwards from Notifications → Templates.
-- =====================================================================

update notification_templates
set body = 'SASI MILK SERVICE: {{date}} {{shift}} - {{producerName}} ({{producerNumber}}). ' ||
           'Milk {{litres}} L @ Rs.{{ratePerLitre}}/L = Rs.{{milkAmount}}. ' ||
           'Incentive Rs.{{incentiveAmount}}, Deduction Rs.{{deductionAmount}}. ' ||
           'Net payable Rs.{{amount}}. Collection No {{collectionNumber}}.'
where code = 'COLLECTION_CREATED' and channel = 'SMS';
