package com.sasimilkservice.report.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-side of the system. Reports may query across module tables directly
 * (CQRS read model) rather than through domain services; all queries are
 * read-only and parameterised.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String centerName() {
        try {
            return jdbc.queryForObject("select center_name from center order by id limit 1", String.class);
        } catch (RuntimeException e) {
            return "SASI MILK SERVICE";
        }
    }

    public List<DailyCollectionRow> dailyCollection(LocalDate from, LocalDate to) {
        return jdbc.query("""
                select collection_date,
                       coalesce(sum(milk_quantity_litres) filter (where shift = 'MORNING'), 0) morning_litres,
                       coalesce(sum(milk_quantity_litres) filter (where shift = 'EVENING'), 0) evening_litres,
                       coalesce(sum(milk_quantity_litres), 0) total_litres,
                       count(distinct producer_id) producer_count,
                       count(*) collection_count,
                       coalesce(sum(base_amount), 0) total_amount
                from milk_collections
                where status = 'CONFIRMED' and collection_date between ? and ?
                group by collection_date
                order by collection_date
                """,
                (rs, i) -> {
                    BigDecimal total = rs.getBigDecimal("total_litres");
                    BigDecimal amount = rs.getBigDecimal("total_amount");
                    BigDecimal avg = total.signum() == 0 ? BigDecimal.ZERO
                            : amount.divide(total, 4, RoundingMode.HALF_UP);
                    return new DailyCollectionRow(
                            rs.getObject("collection_date", LocalDate.class),
                            rs.getBigDecimal("morning_litres"), rs.getBigDecimal("evening_litres"),
                            total, rs.getInt("producer_count"), rs.getInt("collection_count"),
                            avg, amount);
                }, from, to);
    }

    public List<RegionRow> regionReport(LocalDate from, LocalDate to) {
        return jdbc.query("""
                select coalesce(r.code, 'UNASSIGNED') region_code,
                       coalesce(r.name, 'Unassigned') region_name,
                       count(distinct c.producer_id) producer_count,
                       coalesce(sum(c.milk_quantity_litres), 0) total_litres,
                       coalesce(sum(c.base_amount), 0) total_amount
                from milk_collections c
                left join regions r on r.id = c.region_id
                where c.status = 'CONFIRMED' and c.collection_date between ? and ?
                group by r.code, r.name
                order by region_code
                """,
                (rs, i) -> {
                    BigDecimal total = rs.getBigDecimal("total_litres");
                    BigDecimal amount = rs.getBigDecimal("total_amount");
                    BigDecimal avg = total.signum() == 0 ? BigDecimal.ZERO
                            : amount.divide(total, 4, RoundingMode.HALF_UP);
                    return new RegionRow(rs.getString("region_code"), rs.getString("region_name"),
                            rs.getInt("producer_count"), total, avg, amount);
                }, from, to);
    }

    public List<PaymentRow> settlementPayments(long settlementId) {
        return jdbc.query("""
                select si.producer_id, si.total_litres, si.gross_amount, si.total_deduction,
                       si.advance_recovery, si.net_payable, si.payment_status,
                       p.utr, p.status payout_status, p.provider
                from settlement_items si
                left join payouts p on p.settlement_item_id = si.id
                where si.settlement_id = ?
                order by si.producer_id
                """,
                (rs, i) -> new PaymentRow(
                        rs.getLong("producer_id"), rs.getBigDecimal("total_litres"),
                        rs.getBigDecimal("gross_amount"), rs.getBigDecimal("total_deduction"),
                        rs.getBigDecimal("advance_recovery"), rs.getBigDecimal("net_payable"),
                        rs.getString("payment_status"), rs.getString("utr"),
                        rs.getString("payout_status"), rs.getString("provider")),
                settlementId);
    }

    /** Header facts of a payment batch, for the subtitle of its summary report. */
    public BatchHeader batchHeader(long settlementId) {
        List<BatchHeader> rows = jdbc.query("""
                select s.settlement_number, s.payment_frequency, s.period_start, s.period_end, s.status,
                       s.total_producers,
                       (select count(*) from settlement_items i where i.settlement_id = s.id
                           and i.bill_number is not null) bills,
                       (select b.submitted_on from settlement_bank_submissions b where b.settlement_id = s.id) submitted_on,
                       (select b.bank_reference from settlement_bank_submissions b where b.settlement_id = s.id) bank_ref
                from settlements s where s.id = ?
                """,
                (rs, i) -> new BatchHeader(rs.getString("settlement_number"), rs.getString("payment_frequency"),
                        rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                        rs.getString("status"), rs.getInt("total_producers"), rs.getInt("bills"),
                        rs.getObject("submitted_on", LocalDate.class), rs.getString("bank_ref")),
                settlementId);
        if (rows.isEmpty()) {
            throw com.sasimilkservice.common.error.NotFoundException.of("Settlement", settlementId);
        }
        return rows.get(0);
    }

    /** One row per producer of a payment batch: the working behind the consolidated bank submission. */
    public List<BatchLine> batchLines(long settlementId) {
        return batchLines(settlementId, null, null);
    }

    /**
     * @param regionId only this region/area ({@code 0} = producers with no region); null = everyone
     * @param sort     producerId (default) | name | region | net (highest first) | litres (highest first)
     */
    public List<BatchLine> batchLines(long settlementId, Long regionId, String sort) {
        String order = switch (sort == null ? "" : sort) {
            case "name" -> "lower(pr.producer_name), pr.producer_number";
            case "region" -> "coalesce(r.name, 'zzz'), pr.producer_number";
            case "net" -> "si.net_payable desc, pr.producer_number";
            case "litres" -> "si.total_litres desc, pr.producer_number";
            default -> "pr.producer_number";
        };
        return jdbc.query("""
                select pr.producer_number, pr.producer_name, coalesce(r.name, '') region_name,
                       si.total_litres, si.milk_amount, si.incentive_amount, si.total_deduction,
                       si.advance_recovery, si.adjustment_amount, si.net_payable,
                       si.bill_number, si.payment_status, si.payment_reference
                from settlement_items si
                join producers pr on pr.id = si.producer_id
                left join regions r on r.id = si.region_id
                where si.settlement_id = ?
                  and (?::bigint is null or (?::bigint = 0 and si.region_id is null) or si.region_id = ?::bigint)
                 """ + " order by " + order,
                (rs, i) -> new BatchLine(rs.getString("producer_number"), rs.getString("producer_name"),
                        rs.getString("region_name"), rs.getBigDecimal("total_litres"),
                        rs.getBigDecimal("milk_amount"), rs.getBigDecimal("incentive_amount"),
                        rs.getBigDecimal("total_deduction"), rs.getBigDecimal("advance_recovery"),
                        rs.getBigDecimal("adjustment_amount"), rs.getBigDecimal("net_payable"),
                        rs.getString("bill_number"), rs.getString("payment_status"),
                        rs.getString("payment_reference")),
                settlementId, regionId, regionId, regionId);
    }

    /** Everything the Payment Dashboard shows, for batches whose period starts within [from, to]. */
    public PaymentDashboard paymentDashboard(LocalDate from, LocalDate to) {
        Map<String, Object> t = jdbc.queryForMap("""
                select coalesce(sum(total_net_payable) filter (where payment_frequency = 'WEEKLY'), 0) weekly,
                       coalesce(sum(total_net_payable) filter (where payment_frequency = 'MONTHLY'), 0) monthly,
                       coalesce(sum(total_net_payable), 0) total
                from settlements where status <> 'CANCELLED' and period_start between ? and ?
                """, from, to);
        BigDecimal driver = jdbc.queryForObject("""
                select coalesce(sum(total_net), 0) from driver_salary_batches
                where status <> 'CANCELLED' and period_month between ? and ?
                """, BigDecimal.class, from.withDayOfMonth(1), to);
        Integer bills = jdbc.queryForObject("""
                select count(*) from settlement_items si join settlements s on s.id = si.settlement_id
                where si.bill_number is not null and s.status <> 'CANCELLED' and s.period_start between ? and ?
                """, Integer.class, from, to);
        Map<String, Object> pend = jdbc.queryForMap("""
                select count(*) c, coalesce(sum(total_net_payable), 0) a from settlements s
                where s.status = 'APPROVED' and not exists
                      (select 1 from settlement_bank_submissions b where b.settlement_id = s.id)
                  and s.period_start between ? and ?
                """, from, to);
        Map<String, Object> sub = jdbc.queryForMap("""
                select count(*) c, coalesce(sum(total_net_payable), 0) a from settlements s
                where s.status in ('PAYOUT_INITIATED','PARTIALLY_PAID') and s.period_start between ? and ?
                """, from, to);
        BigDecimal paid = jdbc.queryForObject("""
                select coalesce(sum(si.net_payable), 0) from settlement_items si
                join settlements s on s.id = si.settlement_id
                where si.payment_status = 'SUCCESS' and s.period_start between ? and ?
                """, BigDecimal.class, from, to);

        List<DashboardBatch> recent = jdbc.query("""
                select id, settlement_number, payment_frequency, period_start, period_end, status,
                       total_producers, total_net_payable
                from settlements where period_start between ? and ? and status <> 'CANCELLED'
                order by period_start desc, id desc limit 8
                """, this::batchRow, from, to);
        List<DashboardBatch> pending = jdbc.query("""
                select s.id, s.settlement_number, s.payment_frequency, s.period_start, s.period_end, s.status,
                       s.total_producers, s.total_net_payable
                from settlements s
                where s.status = 'APPROVED' and not exists
                      (select 1 from settlement_bank_submissions b where b.settlement_id = s.id)
                order by s.period_start
                """, this::batchRow);
        List<DashboardBill> recentBills = jdbc.query("""
                select si.settlement_id, si.id item_id, si.bill_number, pr.producer_number, pr.producer_name,
                       si.net_payable, si.bill_generated_at
                from settlement_items si join producers pr on pr.id = si.producer_id
                where si.bill_number is not null
                order by si.bill_generated_at desc, si.id desc limit 8
                """,
                (rs, i) -> new DashboardBill(rs.getLong("settlement_id"), rs.getLong("item_id"),
                        rs.getString("bill_number"), rs.getString("producer_number"), rs.getString("producer_name"),
                        rs.getBigDecimal("net_payable"), rs.getTimestamp("bill_generated_at").toInstant().toString()));
        List<DashboardTrend> trend = jdbc.query("""
                select period_start, payment_frequency, total_net_payable from settlements
                where status <> 'CANCELLED' and payment_frequency in ('WEEKLY','MONTHLY')
                order by period_start desc, id desc limit 12
                """,
                (rs, i) -> new DashboardTrend(rs.getObject("period_start", LocalDate.class),
                        rs.getString("payment_frequency"), rs.getBigDecimal("total_net_payable")));
        java.util.Collections.reverse(trend);
        List<DashboardActivity> activity = jdbc.query("""
                select h.settlement_id, s.settlement_number, h.subject_type, h.from_status, h.to_status, h.note,
                       h.changed_at
                from settlement_status_history h join settlements s on s.id = h.settlement_id
                order by h.id desc limit 10
                """,
                (rs, i) -> new DashboardActivity(rs.getLong("settlement_id"), rs.getString("settlement_number"),
                        rs.getString("subject_type"), rs.getString("from_status"), rs.getString("to_status"),
                        rs.getString("note"), rs.getTimestamp("changed_at").toInstant().toString()));

        return new PaymentDashboard(from, to, (BigDecimal) t.get("total"), (BigDecimal) t.get("weekly"),
                (BigDecimal) t.get("monthly"), driver, bills == null ? 0 : bills,
                ((Number) pend.get("c")).intValue(), (BigDecimal) pend.get("a"),
                ((Number) sub.get("c")).intValue(), (BigDecimal) sub.get("a"), paid,
                recent, pending, recentBills, trend, activity, upcomingPayments());
    }

    /** The payment-side reports, each a flat table over batches whose period starts within [from, to]. */
    public com.sasimilkservice.report.export.TabularReport paymentReport(String kind, LocalDate from, LocalDate to) {
        String range = "%s to %s".formatted(from, to);
        return switch (kind) {
            case "pending" -> table("Pending Payments Report", range,
                    List.of("Batch", "Period", "Producer ID", "Name", "Net Payable", "Bill No.", "Status"),
                    """
                    select s.settlement_number, s.period_start || ' to ' || s.period_end, pr.producer_number,
                           pr.producer_name, si.net_payable, coalesce(si.bill_number, ''),
                           case si.payment_status when 'ON_HOLD' then 'On hold'
                                when 'INITIATED' then 'Submitted to bank' when 'PROCESSING' then 'Submitted to bank'
                                else 'Pending' end
                    from settlement_items si
                    join settlements s on s.id = si.settlement_id
                    join producers pr on pr.id = si.producer_id
                    where si.payment_status in ('PENDING','INITIATED','PROCESSING','ON_HOLD')
                      and si.net_payable > 0 and s.status not in ('CANCELLED','DRAFT','FAILED')
                      and s.period_start between ? and ?
                    order by s.period_start, pr.producer_number
                    """, from, to);
            case "bank-submissions" -> table("Bank Submission Report", range,
                    List.of("Batch", "Type", "Period", "Submitted On", "Bank Reference", "Producers", "Amount", "Note", "Batch Status"),
                    """
                    select s.settlement_number, s.payment_frequency, s.period_start || ' to ' || s.period_end,
                           b.submitted_on::text, coalesce(b.bank_reference, ''), b.producer_count, b.total_amount,
                           coalesce(b.note, ''), s.status
                    from settlement_bank_submissions b join settlements s on s.id = b.settlement_id
                    where b.submitted_on between ? and ?
                    order by b.submitted_on desc, s.id desc
                    """, from, to);
            case "confirmations" -> table("Payment Confirmation Report", range,
                    List.of("Batch", "Producer ID", "Name", "Amount", "Bank Reference (UTR)", "Confirmed On"),
                    """
                    select s.settlement_number, pr.producer_number, pr.producer_name, p.amount, coalesce(p.utr, ''),
                           coalesce(p.settled_at::date::text, '')
                    from payouts p
                    join settlements s on s.id = p.settlement_id
                    join producers pr on pr.id = p.producer_id
                    where p.status = 'SUCCESS' and coalesce(p.settled_at::date, s.period_end) between ? and ?
                    order by p.settled_at desc nulls last, p.id desc
                    """, from, to);
            case "incentives-deductions" -> table("Incentive & Deduction Report", range,
                    List.of("Batch", "Period", "Producers", "Milk Amount", "Incentive", "Feed", "Veterinary", "Other Deductions",
                            "Advance Recovery", "Adjustments", "Net Payable"),
                    """
                    select s.settlement_number, s.period_start || ' to ' || s.period_end, count(si.id),
                           coalesce(sum(si.milk_amount),0), coalesce(sum(si.incentive_amount),0),
                           coalesce(sum(si.feed_deduction),0), coalesce(sum(si.veterinary_deduction),0),
                           coalesce(sum(si.other_deduction),0), coalesce(sum(si.advance_recovery),0),
                           coalesce(sum(si.adjustment_amount),0), coalesce(sum(si.net_payable),0)
                    from settlements s left join settlement_items si on si.settlement_id = s.id
                    where s.status <> 'CANCELLED' and s.period_start between ? and ?
                    group by s.id order by s.period_start desc, s.id desc
                    """, from, to);
            case "period-summary" -> table("Period-wise Payment Summary", range,
                    List.of("Batch", "Type", "Period", "Producers", "Litres", "Milk Amount", "Incentive", "Deductions",
                            "Net Payable", "Bills", "Status"),
                    """
                    select s.settlement_number, case s.payment_frequency when 'WEEKLY' then 'Weekly'
                               when 'MONTHLY' then 'Monthly' else 'Other' end,
                           s.period_start || ' to ' || s.period_end, s.total_producers, s.total_litres,
                           s.total_milk_amount, s.total_incentive, s.total_deduction, s.total_net_payable,
                           (select count(*) from settlement_items i where i.settlement_id = s.id and i.bill_number is not null),
                           s.status
                    from settlements s
                    where s.status <> 'CANCELLED' and s.period_start between ? and ?
                    order by s.period_start desc, s.id desc
                    """, from, to);
            default -> throw new com.sasimilkservice.common.error.BusinessRuleException("Unknown payment report: " + kind);
        };
    }

    private com.sasimilkservice.report.export.TabularReport table(String title, String subtitle, List<String> headers,
                                                                  String sql, Object... args) {
        int cols = headers.size();
        List<List<Object>> rows = jdbc.query(sql, (rs, i) -> {
            List<Object> r = new java.util.ArrayList<>(cols);
            for (int c = 1; c <= cols; c++) {
                r.add(rs.getObject(c));
            }
            return r;
        }, args);
        return com.sasimilkservice.report.export.TabularReport.of(title, subtitle, headers, rows);
    }

    /** The weekly, monthly and driver-salary runs for the most recently finished periods that are not complete yet. */
    private List<UpcomingPayment> upcomingPayments() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.minusDays((today.getDayOfWeek().getValue() + 6L) % 7);
        LocalDate lastMonday = thisMonday.minusDays(7);
        LocalDate lastMonthStart = today.withDayOfMonth(1).minusMonths(1);
        List<UpcomingPayment> out = new java.util.ArrayList<>();

        addFarmerRun(out, "WEEKLY", "Weekly farmer payment", lastMonday, lastMonday.plusDays(6));
        addFarmerRun(out, "MONTHLY", "Monthly farmer payment", lastMonthStart, lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth()));

        List<Map<String, Object>> sal = jdbc.queryForList(
                "select id, status from driver_salary_batches where period_month = ? and status <> 'CANCELLED' order by id desc limit 1",
                lastMonthStart);
        if (sal.isEmpty()) {
            out.add(new UpcomingPayment("DRIVER_SALARY", "Driver salary", lastMonthStart,
                    lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth()), null, "NOT_STARTED"));
        } else if (!"PAID".equals(sal.get(0).get("status"))) {
            out.add(new UpcomingPayment("DRIVER_SALARY", "Driver salary", lastMonthStart,
                    lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth()),
                    ((Number) sal.get(0).get("id")).longValue(), (String) sal.get(0).get("status")));
        }
        return out;
    }

    /** Adds the run for a period unless every batch for it is already paid in full. */
    private void addFarmerRun(List<UpcomingPayment> out, String frequency, String label, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, status from settlements where payment_frequency = ? and period_start = ? and status <> 'CANCELLED' order by id",
                frequency, from);
        if (rows.isEmpty()) {
            out.add(new UpcomingPayment(frequency, label, from, to, null, "NOT_STARTED"));
            return;
        }
        for (Map<String, Object> r : rows) {
            if (!"PAID".equals(r.get("status"))) {
                out.add(new UpcomingPayment(frequency, label, from, to, ((Number) r.get("id")).longValue(),
                        (String) r.get("status")));
            }
        }
    }

    private DashboardBatch batchRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new DashboardBatch(rs.getLong("id"), rs.getString("settlement_number"),
                rs.getString("payment_frequency"), rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class), rs.getString("status"),
                rs.getInt("total_producers"), rs.getBigDecimal("total_net_payable"));
    }

    /** Business name, address and phone for report letterheads. */
    public CenterLine centerLine() {
        List<CenterLine> r = jdbc.query("select center_name, address, mobile_number from center order by id limit 1",
                (rs, i) -> new CenterLine(rs.getString(1), rs.getString(2), rs.getString(3)));
        return r.isEmpty() ? new CenterLine("SASI MILK SERVICE", null, null) : r.get(0);
    }

    public record CenterLine(String name, String address, String mobile) {
    }

    /**
     * Everything a producer wants on paper for a period: each collection (date, shift, time, litres, FAT, SNF,
     * rate, amount), totals, deductions, advances and what was paid. Figures are read from the stored records —
     * nothing is recalculated.
     */
    public DetailedStatement detailedStatement(long producerId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> head = jdbc.queryForList("""
                select p.producer_number, p.producer_name, p.mobile_number, p.address, r.name region_name
                from producers p left join regions r on r.id = p.region_id where p.id = ?
                """, producerId);
        if (head.isEmpty()) {
            throw com.sasimilkservice.common.error.NotFoundException.of("Producer", producerId);
        }
        Map<String, Object> h = head.get(0);

        List<StatementCollection> rows = jdbc.query("""
                select collection_date, shift, collection_number,
                       to_char(created_at at time zone 'Asia/Kolkata', 'HH24:MI') recorded_at,
                       milk_quantity_litres, fat_percentage, snf_percentage, applicable_rate,
                       base_amount, incentive_amount
                from milk_collections
                where producer_id = ? and status = 'CONFIRMED' and collection_date between ? and ?
                order by collection_date, case shift when 'MORNING' then 0 else 1 end, id
                """,
                (rs, i) -> new StatementCollection(rs.getObject("collection_date", LocalDate.class),
                        rs.getString("shift"), rs.getString("collection_number"), rs.getString("recorded_at"),
                        rs.getBigDecimal("milk_quantity_litres"), rs.getBigDecimal("fat_percentage"),
                        rs.getBigDecimal("snf_percentage"), rs.getBigDecimal("applicable_rate"),
                        rs.getBigDecimal("base_amount"), rs.getBigDecimal("incentive_amount")),
                producerId, from, to);

        List<StatementCharge> deductions = jdbc.query("""
                select dt.name, coalesce(sum(d.amount), 0) amount
                from deduction_transactions d join deduction_types dt on dt.id = d.deduction_type_id
                where d.producer_id = ? and d.status <> 'CANCELLED' and d.txn_date between ? and ?
                group by dt.name order by dt.name
                """,
                (rs, i) -> new StatementCharge(rs.getString(1), rs.getBigDecimal(2)), producerId, from, to);

        BigDecimal advTaken = jdbc.queryForObject("""
                select coalesce(sum(original_amount), 0) from advance_accounts
                where producer_id = ? and status <> 'CANCELLED' and advance_date between ? and ?
                """, BigDecimal.class, producerId, from, to);
        BigDecimal advRecovered = jdbc.queryForObject("""
                select coalesce(sum(case txn_type when 'RECOVERY' then amount when 'REVERSAL' then -amount else 0 end), 0)
                from advance_transactions where producer_id = ? and txn_date between ? and ?
                """, BigDecimal.class, producerId, from, to);
        BigDecimal advOutstanding = jdbc.queryForObject("""
                select coalesce(sum(outstanding_amount), 0) from advance_accounts
                where producer_id = ? and status <> 'CANCELLED'
                """, BigDecimal.class, producerId);

        List<StatementPayment> payments = jdbc.query("""
                select s.settlement_number, s.period_start, s.period_end, si.bill_number, si.milk_amount,
                       si.incentive_amount, si.total_deduction, si.net_payable, si.payment_status, si.payment_reference
                from settlement_items si join settlements s on s.id = si.settlement_id
                where si.producer_id = ? and s.status <> 'CANCELLED' and s.period_end >= ? and s.period_start <= ?
                order by s.period_start, s.id
                """,
                (rs, i) -> new StatementPayment(rs.getString("settlement_number"),
                        rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                        rs.getString("bill_number"), rs.getBigDecimal("milk_amount"),
                        rs.getBigDecimal("incentive_amount"), rs.getBigDecimal("total_deduction"),
                        rs.getBigDecimal("net_payable"), rs.getString("payment_status"),
                        rs.getString("payment_reference")),
                producerId, from, to);

        return new DetailedStatement(producerId, (String) h.get("producer_number"), (String) h.get("producer_name"),
                (String) h.get("mobile_number"), (String) h.get("address"), (String) h.get("region_name"),
                from, to, rows, deductions, advTaken, advRecovered, advOutstanding, payments);
    }

    public record StatementCollection(LocalDate date, String shift, String collectionNumber, String recordedAt,
                                      BigDecimal litres, BigDecimal fat, BigDecimal snf, BigDecimal rate,
                                      BigDecimal milkAmount, BigDecimal incentive) {
        public BigDecimal total() {
            return milkAmount.add(incentive);
        }
    }

    public record StatementCharge(String name, BigDecimal amount) {
    }

    public record StatementPayment(String batch, LocalDate periodStart, LocalDate periodEnd, String billNumber,
                                   BigDecimal milk, BigDecimal incentive, BigDecimal deductions,
                                   BigDecimal netPayable, String status, String reference) {
    }

    public record DetailedStatement(long producerId, String producerNumber, String producerName, String mobile,
                                    String address, String region, LocalDate from, LocalDate to,
                                    List<StatementCollection> collections, List<StatementCharge> deductions,
                                    BigDecimal advanceTaken, BigDecimal advanceRecovered,
                                    BigDecimal advanceOutstanding, List<StatementPayment> payments) {
        public BigDecimal totalLitres() {
            return collections.stream().map(StatementCollection::litres).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal morningLitres() {
            return litresOf("MORNING");
        }

        public BigDecimal eveningLitres() {
            return litresOf("EVENING");
        }

        private BigDecimal litresOf(String shift) {
            return collections.stream().filter(c -> shift.equals(c.shift())).map(StatementCollection::litres)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal milkAmount() {
            return collections.stream().map(StatementCollection::milkAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal incentive() {
            return collections.stream().map(StatementCollection::incentive).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal gross() {
            return milkAmount().add(incentive());
        }

        public BigDecimal totalDeductions() {
            return deductions.stream().map(StatementCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Litres-weighted average of the rate actually applied. */
        public BigDecimal averageRate() {
            BigDecimal l = totalLitres();
            return l.signum() == 0 ? BigDecimal.ZERO : milkAmount().divide(l, 4, java.math.RoundingMode.HALF_UP);
        }

        public long collectionDays() {
            return collections.stream().map(StatementCollection::date).distinct().count();
        }
    }

    public ProducerStatement producerStatement(long producerId, LocalDate from, LocalDate to) {
        Map<String, Object> totals = jdbc.queryForMap("""
                select coalesce(sum(milk_quantity_litres), 0) total_litres,
                       coalesce(sum(base_amount), 0) total_milk_amount,
                       coalesce(sum(incentive_amount), 0) total_incentive,
                       count(*) collection_count
                from milk_collections
                where producer_id = ? and status = 'CONFIRMED' and collection_date between ? and ?
                """, producerId, from, to);

        BigDecimal deductions = jdbc.queryForObject("""
                select coalesce(sum(amount), 0) from deduction_transactions
                where producer_id = ? and status <> 'CANCELLED' and txn_date between ? and ?
                """, BigDecimal.class, producerId, from, to);

        Map<String, Object> advance = jdbc.queryForMap("""
                select coalesce(sum(original_amount), 0) total_advance,
                       coalesce(sum(original_amount - outstanding_amount), 0) recovered,
                       coalesce(sum(outstanding_amount), 0) outstanding
                from advance_accounts
                where producer_id = ? and status <> 'CANCELLED'
                """, producerId);

        BigDecimal paid = jdbc.queryForObject("""
                select coalesce(sum(amount), 0) from payouts
                where producer_id = ? and status = 'SUCCESS'
                """, BigDecimal.class, producerId);

        return new ProducerStatement(
                producerId, from, to,
                (BigDecimal) totals.get("total_litres"),
                (BigDecimal) totals.get("total_milk_amount"),
                (BigDecimal) totals.get("total_incentive"),
                ((Number) totals.get("collection_count")).intValue(),
                deductions,
                (BigDecimal) advance.get("total_advance"),
                (BigDecimal) advance.get("recovered"),
                (BigDecimal) advance.get("outstanding"),
                paid);
    }

    // ---- row types ----

    public record PaymentDashboard(LocalDate from, LocalDate to, BigDecimal totalPayable, BigDecimal weeklyPayable,
                                   BigDecimal monthlyPayable, BigDecimal driverSalaryPayable, int billsGenerated,
                                   int pendingSubmissionCount, BigDecimal pendingSubmissionAmount,
                                   int submittedCount, BigDecimal submittedAmount, BigDecimal confirmedPaid,
                                   List<DashboardBatch> recentBatches, List<DashboardBatch> pendingSubmissions,
                                   List<DashboardBill> recentBills, List<DashboardTrend> trend,
                                   List<DashboardActivity> recentActivity,
                                   List<UpcomingPayment> upcoming) {
    }

    /** A payment run that should happen next. {@code status} is NOT_STARTED until a batch exists. */
    public record UpcomingPayment(String kind, String label, LocalDate periodStart, LocalDate periodEnd,
                                  Long batchId, String status) {
    }

    public record DashboardBatch(long id, String number, String frequency, LocalDate periodStart,
                                 LocalDate periodEnd, String status, int producers, BigDecimal netPayable) {
    }

    public record DashboardBill(long settlementId, long itemId, String billNumber, String producerNumber,
                                String producerName, BigDecimal netPayable, String generatedAt) {
    }

    public record DashboardTrend(LocalDate periodStart, String frequency, BigDecimal netPayable) {
    }

    public record DashboardActivity(long settlementId, String settlementNumber, String subjectType,
                                    String fromStatus, String toStatus, String note, String changedAt) {
    }

    public record BatchHeader(String number, String frequency, LocalDate periodStart, LocalDate periodEnd,
                              String status, int producers, int bills, LocalDate submittedOn, String bankReference) {
    }

    public record BatchLine(String producerNumber, String producerName, String region, BigDecimal litres,
                            BigDecimal milkAmount, BigDecimal incentive, BigDecimal deductions,
                            BigDecimal advanceRecovery, BigDecimal adjustments, BigDecimal netPayable,
                            String billNumber, String paymentStatus, String utr) {
    }

    public record DailyCollectionRow(LocalDate date, BigDecimal morningLitres, BigDecimal eveningLitres,
                                     BigDecimal totalLitres, int producerCount, int collectionCount,
                                     BigDecimal averageRate, BigDecimal totalAmount) {
    }

    public record RegionRow(String regionCode, String regionName, int producerCount,
                            BigDecimal totalLitres, BigDecimal averageRate, BigDecimal totalAmount) {
    }

    public record PaymentRow(long producerId, BigDecimal totalLitres, BigDecimal grossAmount,
                             BigDecimal totalDeduction, BigDecimal advanceRecovery, BigDecimal netPayable,
                             String paymentStatus, String utr, String payoutStatus, String provider) {
    }

    public record ProducerStatement(long producerId, LocalDate from, LocalDate to,
                                    BigDecimal totalLitres, BigDecimal totalMilkAmount,
                                    BigDecimal totalIncentive, int collectionCount,
                                    BigDecimal totalDeductions, BigDecimal totalAdvance,
                                    BigDecimal advanceRecovered, BigDecimal advanceOutstanding,
                                    BigDecimal totalPaid) {
    }
}
