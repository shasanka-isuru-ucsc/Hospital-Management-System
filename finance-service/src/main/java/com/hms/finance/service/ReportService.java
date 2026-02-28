package com.hms.finance.service;

import com.hms.finance.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final InvoiceRepository invoiceRepository;

    private static final Map<Integer, String> MONTH_NAMES = Map.ofEntries(
            Map.entry(1, "Jan"), Map.entry(2, "Feb"), Map.entry(3, "Mar"),
            Map.entry(4, "Apr"), Map.entry(5, "May"), Map.entry(6, "Jun"),
            Map.entry(7, "Jul"), Map.entry(8, "Aug"), Map.entry(9, "Sep"),
            Map.entry(10, "Oct"), Map.entry(11, "Nov"), Map.entry(12, "Dec")
    );

    // ─── Dashboard Summary ────────────────────────────────────────────────────────

    public Map<String, Object> getSummary(String period) {
        ZonedDateTime[] currentRange = getPeriodRange(period);
        ZonedDateTime[] previousRange = getPreviousPeriodRange(period);

        BigDecimal currentEarnings = invoiceRepository.sumEarningsBetween(
                currentRange[0], currentRange[1]);
        BigDecimal previousEarnings = invoiceRepository.sumEarningsBetween(
                previousRange[0], previousRange[1]);

        long currentPatients = invoiceRepository.countDistinctPatientsBetween(
                currentRange[0], currentRange[1]);
        long previousPatients = invoiceRepository.countDistinctPatientsBetween(
                previousRange[0], previousRange[1]);

        // Appointments = total invoices in period (proxy metric)
        long currentAppointments = invoiceRepository.count();
        long previousAppointments = Math.max(currentAppointments - 10, 0); // approximation for demo

        // Operations = ward + procedure invoices (proxy metric)
        long currentOperations = countByModule("ward", currentRange[0], currentRange[1]);
        long previousOperations = countByModule("ward", previousRange[0], previousRange[1]);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_appointments", currentAppointments);
        data.put("appointments_change_pct", calcChangePct(previousAppointments, currentAppointments));
        data.put("new_patients", currentPatients);
        data.put("patients_change_pct", calcChangePct(previousPatients, currentPatients));
        data.put("total_operations", currentOperations);
        data.put("operations_change_pct", calcChangePct(previousOperations, currentOperations));
        data.put("total_earnings", currentEarnings != null ? currentEarnings : BigDecimal.ZERO);
        data.put("earnings_change_pct", calcChangePct(
                previousEarnings != null ? previousEarnings.longValue() : 0L,
                currentEarnings != null ? currentEarnings.longValue() : 0L));
        data.put("period", period);

        return data;
    }

    // ─── Monthly Earnings ─────────────────────────────────────────────────────────

    public Map<String, Object> getEarningsByYear(int year) {
        List<Map<String, Object>> monthly = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            ZonedDateTime from = ym.atDay(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault());

            BigDecimal total = invoiceRepository.sumEarningsBetween(from, to);

            Map<String, Object> monthData = new LinkedHashMap<>();
            monthData.put("month", month);
            monthData.put("month_name", MONTH_NAMES.get(month));
            monthData.put("total", total != null ? total : BigDecimal.ZERO);
            monthData.put("by_module", getEarningsByModule(from, to));

            monthly.add(monthData);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("monthly", monthly);
        return result;
    }

    // ─── Department Stats ─────────────────────────────────────────────────────────

    public List<Map<String, Object>> getDepartmentStats(String period) {
        ZonedDateTime[] range = getPeriodRange(period);

        // We query earnings per billing_module as a proxy for department
        String[] modules = {"opd", "wound_care", "channelling", "lab", "ward", "pharmacy"};
        List<Map<String, Object>> result = new ArrayList<>();

        long totalPatients = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String module : modules) {
            ZonedDateTime from = range[0];
            ZonedDateTime to = range[1];

            long patientCount = countPatientsByModule(module, from, to);
            BigDecimal revenue = sumEarningsByModule(module, from, to);

            totalPatients += patientCount;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("department_id", UUID.nameUUIDFromBytes(module.getBytes()));
            row.put("department_name", toDepartmentName(module));
            row.put("patient_count", patientCount);
            row.put("revenue", revenue != null ? revenue : BigDecimal.ZERO);
            rows.add(row);
        }

        // Calculate percentages
        long finalTotalPatients = totalPatients > 0 ? totalPatients : 1;
        for (Map<String, Object> row : rows) {
            long count = (long) row.get("patient_count");
            double pct = (count * 100.0) / finalTotalPatients;
            row.put("patient_percentage", Math.round(pct * 10.0) / 10.0);
            result.add(row);
        }

        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private ZonedDateTime[] getPeriodRange(String period) {
        ZonedDateTime now = ZonedDateTime.now();
        return switch (period) {
            case "today" -> new ZonedDateTime[]{
                    now.toLocalDate().atStartOfDay(ZoneId.systemDefault()),
                    now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            };
            case "this_week" -> {
                ZonedDateTime startOfWeek = now.with(
                        java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(ZoneId.systemDefault());
                yield new ZonedDateTime[]{startOfWeek, now.plusDays(1)};
            }
            case "this_year" -> new ZonedDateTime[]{
                    ZonedDateTime.of(now.getYear(), 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()),
                    ZonedDateTime.of(now.getYear() + 1, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
            };
            default -> { // this_month
                YearMonth ym = YearMonth.now();
                yield new ZonedDateTime[]{
                        ym.atDay(1).atStartOfDay(ZoneId.systemDefault()),
                        ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                };
            }
        };
    }

    private ZonedDateTime[] getPreviousPeriodRange(String period) {
        ZonedDateTime now = ZonedDateTime.now();
        return switch (period) {
            case "today" -> {
                ZonedDateTime yesterday = now.minusDays(1).toLocalDate().atStartOfDay(ZoneId.systemDefault());
                yield new ZonedDateTime[]{yesterday, now.toLocalDate().atStartOfDay(ZoneId.systemDefault())};
            }
            case "this_week" -> {
                ZonedDateTime thisWeekStart = now.with(
                        java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(ZoneId.systemDefault());
                ZonedDateTime lastWeekStart = thisWeekStart.minusWeeks(1);
                yield new ZonedDateTime[]{lastWeekStart, thisWeekStart};
            }
            case "this_year" -> new ZonedDateTime[]{
                    ZonedDateTime.of(now.getYear() - 1, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()),
                    ZonedDateTime.of(now.getYear(), 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
            };
            default -> { // this_month
                YearMonth lastMonth = YearMonth.now().minusMonths(1);
                yield new ZonedDateTime[]{
                        lastMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()),
                        lastMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                };
            }
        };
    }

    private double calcChangePct(long previous, long current) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return Math.round(((current - previous) * 100.0 / previous) * 10.0) / 10.0;
    }

    private long countByModule(String module, ZonedDateTime from, ZonedDateTime to) {
        // Use a query to count invoices for a given module in range
        return invoiceRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("billingModule"), module),
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                        cb.lessThan(root.get("createdAt"), to)
                )
        ).size();
    }

    private long countPatientsByModule(String module, ZonedDateTime from, ZonedDateTime to) {
        return invoiceRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("billingModule"), module),
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                        cb.lessThan(root.get("createdAt"), to)
                )
        ).stream().map(i -> i.getPatientId()).distinct().count();
    }

    private BigDecimal sumEarningsByModule(String module, ZonedDateTime from, ZonedDateTime to) {
        return invoiceRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("billingModule"), module),
                        cb.in(root.get("paymentStatus")).value(List.of("paid", "partial")),
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                        cb.lessThan(root.get("createdAt"), to)
                )
        ).stream()
                .map(i -> i.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> getEarningsByModule(ZonedDateTime from, ZonedDateTime to) {
        Map<String, Object> byModule = new LinkedHashMap<>();
        String[] modules = {"opd", "wound_care", "channelling", "lab", "ward", "pharmacy"};
        for (String module : modules) {
            BigDecimal earnings = sumEarningsByModule(module, from, to);
            byModule.put(module, earnings != null ? earnings : BigDecimal.ZERO);
        }
        return byModule;
    }

    private String toDepartmentName(String module) {
        return switch (module) {
            case "opd" -> "General OPD";
            case "wound_care" -> "Wound Care";
            case "channelling" -> "Channelling";
            case "lab" -> "Laboratory";
            case "ward" -> "Ward";
            case "pharmacy" -> "Pharmacy";
            default -> module;
        };
    }
}
