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
