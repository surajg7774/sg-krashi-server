package com.sgkrashi.analytics.service.impl;

import com.sgkrashi.analytics.dto.response.ConversionReportResponse;
import com.sgkrashi.analytics.dto.response.OccupancyReportResponse;
import com.sgkrashi.analytics.dto.response.RevenueReportResponse;
import com.sgkrashi.analytics.dto.response.TopListingsResponse;
import com.sgkrashi.analytics.repository.AnalyticsQueryRepository;
import com.sgkrashi.analytics.service.AnalyticsService;
import com.sgkrashi.analytics.util.CsvWriter;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.equipmentrental.repository.EquipmentRepository;
import com.sgkrashi.farmstay.repository.StayListingRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every report method here does exactly one thing beyond calling the
 * repository: reshape already-aggregated SQL result rows into response DTOs
 * (pivoting the revenue rows by payable_type, defaulting occupancy to 0% for
 * listings with no bookings, summing per-module conversion rows into an
 * overall total). None of that is entity-list aggregation — see {@code
 * AnalyticsQueryRepository}'s Javadoc for where the real GROUP BY/SUM/COUNT
 * work happens.
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String PAYABLE_TYPE_ORDER = "ORDER";
    private static final String PAYABLE_TYPE_BOOKING = "BOOKING";
    private static final int DEFAULT_LIMIT = 10;

    private final AnalyticsQueryRepository analyticsQueryRepository;
    private final EquipmentRepository equipmentRepository;
    private final StayListingRepository stayListingRepository;

    public AnalyticsServiceImpl(
            AnalyticsQueryRepository analyticsQueryRepository,
            EquipmentRepository equipmentRepository,
            StayListingRepository stayListingRepository
    ) {
        this.analyticsQueryRepository = analyticsQueryRepository;
        this.equipmentRepository = equipmentRepository;
        this.stayListingRepository = stayListingRepository;
    }

    @Override
    public RevenueReportResponse getRevenueReport(Instant from, Instant to, String groupBy) {
        String datePattern = switch (groupBy == null ? "day" : groupBy) {
            case "week" -> "%x-%v"; // ISO year-week, e.g. "2026-30"
            case "month" -> "%Y-%m";
            default -> "%Y-%m-%d";
        };

        List<Object[]> rows = analyticsQueryRepository.findRevenueByBucket(datePattern, from, to);

        // Pivot: the SQL query already grouped by (bucket, payableType); this
        // just merges the (at most) two rows per bucket into one point.
        Map<String, BigDecimal[]> byBucket = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String bucket = (String) row[0];
            String payableType = (String) row[1];
            BigDecimal amount = toBigDecimal(row[2]);
            BigDecimal[] entry = byBucket.computeIfAbsent(bucket, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (PAYABLE_TYPE_ORDER.equals(payableType)) {
                entry[0] = entry[0].add(amount);
            } else if (PAYABLE_TYPE_BOOKING.equals(payableType)) {
                entry[1] = entry[1].add(amount);
            }
        }

        List<RevenueReportResponse.RevenuePoint> points = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> entry : byBucket.entrySet()) {
            BigDecimal orderRevenue = entry.getValue()[0];
            BigDecimal bookingRevenue = entry.getValue()[1];
            BigDecimal bucketTotal = orderRevenue.add(bookingRevenue);
            points.add(new RevenueReportResponse.RevenuePoint(entry.getKey(), orderRevenue, bookingRevenue, bucketTotal));
            total = total.add(bucketTotal);
        }
        return new RevenueReportResponse(points, total);
    }

    @Override
    public TopListingsResponse getTopListings(String type, Instant from, Instant to, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        List<Object[]> rows = switch (type) {
            case "PRODUCT" -> analyticsQueryRepository.findTopProducts(from, to, effectiveLimit);
            case "CROP_LISTING" -> analyticsQueryRepository.findTopCropListings(from, to, effectiveLimit);
            case "EQUIPMENT" -> analyticsQueryRepository.findTopEquipment(from, to, effectiveLimit);
            case "STAY" -> analyticsQueryRepository.findTopStayListings(from, to, effectiveLimit);
            default -> throw new BusinessRuleException("Unknown listing type: " + type);
        };

        List<TopListingsResponse.TopListingItem> items = rows.stream()
                .map(row -> new TopListingsResponse.TopListingItem(toLong(row[0]), (String) row[1], toLong(row[2]), toBigDecimal(row[3])))
                .toList();
        return new TopListingsResponse(items);
    }

    @Override
    public OccupancyReportResponse getOccupancyReport(String bookableType, LocalDate from, LocalDate to) {
        long rangeDays = ChronoUnit.DAYS.between(from, to);
        if (rangeDays <= 0) {
            throw new BusinessRuleException("'to' must be after 'from'");
        }

        Map<Long, Integer> bookedDaysById = new LinkedHashMap<>();
        for (Object[] row : analyticsQueryRepository.findBookedDaysByBookable(bookableType, from, to)) {
            bookedDaysById.put(toLong(row[0]), (int) toLong(row[1]));
        }

        // Every ACTIVE listing of this type is included, even ones with zero
        // bookings in the window (0% occupancy is a real, useful answer) —
        // these two repository calls are simple name/id lookups (small,
        // already-existing Equipment/StayListing tables), not the aggregation
        // itself, which is entirely done above in SQL.
        List<OccupancyReportResponse.OccupancyItem> items;
        if ("EQUIPMENT".equals(bookableType)) {
            items = equipmentRepository.findByIsActiveTrue(Pageable.unpaged()).getContent().stream()
                    .map(equipment -> buildOccupancyItem(equipment.getId(), equipment.getName(), bookedDaysById, rangeDays))
                    .toList();
        } else if ("STAY".equals(bookableType)) {
            items = stayListingRepository.findByIsActiveTrue(Pageable.unpaged()).getContent().stream()
                    .map(stay -> buildOccupancyItem(stay.getId(), stay.getName(), bookedDaysById, rangeDays))
                    .toList();
        } else {
            throw new BusinessRuleException("Unknown bookable type: " + bookableType);
        }

        return new OccupancyReportResponse(items, (int) rangeDays);
    }

    private OccupancyReportResponse.OccupancyItem buildOccupancyItem(Long id, String name, Map<Long, Integer> bookedDaysById, long rangeDays) {
        int bookedDays = bookedDaysById.getOrDefault(id, 0);
        double rate = rangeDays > 0 ? (double) bookedDays / rangeDays : 0.0;
        return new OccupancyReportResponse.OccupancyItem(id, name, bookedDays, rate);
    }

    @Override
    public ConversionReportResponse getConversionReport(Instant from, Instant to) {
        List<Object[]> rows = analyticsQueryRepository.findConversionByModuleType(from, to);

        List<ConversionReportResponse.ConversionItem> items = new ArrayList<>();
        long overallTotal = 0;
        long overallConverted = 0;
        for (Object[] row : rows) {
            String moduleType = (String) row[0];
            long total = toLong(row[1]);
            long converted = toLong(row[2]);
            items.add(new ConversionReportResponse.ConversionItem(moduleType, total, converted, rate(converted, total)));
            overallTotal += total;
            overallConverted += converted;
        }
        ConversionReportResponse.ConversionItem overall =
                new ConversionReportResponse.ConversionItem("ALL", overallTotal, overallConverted, rate(overallConverted, overallTotal));
        return new ConversionReportResponse(items, overall);
    }

    @Override
    public String exportCsv(String report, Instant from, Instant to, String groupBy, String type, String bookableType, int limit) {
        return switch (report) {
            case "revenue" -> revenueToCsv(getRevenueReport(from, to, groupBy));
            case "top-listings" -> topListingsToCsv(getTopListings(type, from, to, limit));
            case "occupancy" -> occupancyToCsv(getOccupancyReport(bookableType, from.atZone(ADMIN_ZONE).toLocalDate(), to.atZone(ADMIN_ZONE).toLocalDate()));
            case "conversion" -> conversionToCsv(getConversionReport(from, to));
            default -> throw new BusinessRuleException("Unknown report type: " + report);
        };
    }

    private String revenueToCsv(RevenueReportResponse report) {
        List<List<String>> rows = report.points().stream()
                .map(p -> List.of(p.bucket(), p.orderRevenue().toPlainString(), p.bookingRevenue().toPlainString(), p.total().toPlainString()))
                .toList();
        return CsvWriter.write(List.of("Bucket", "Order Revenue", "Booking Revenue", "Total Revenue"), rows);
    }

    private String topListingsToCsv(TopListingsResponse report) {
        List<List<String>> rows = report.items().stream()
                .map(i -> List.of(String.valueOf(i.id()), i.name(), String.valueOf(i.unitsSold()), i.revenue().toPlainString()))
                .toList();
        return CsvWriter.write(List.of("Id", "Name", "Units/Bookings", "Revenue"), rows);
    }

    private String occupancyToCsv(OccupancyReportResponse report) {
        List<List<String>> rows = report.items().stream()
                .map(i -> List.of(String.valueOf(i.id()), i.name(), String.valueOf(i.bookedDays()),
                        String.valueOf(report.rangeDays()), String.format("%.1f%%", i.occupancyRate() * 100)))
                .toList();
        return CsvWriter.write(List.of("Id", "Name", "Booked Days", "Range Days", "Occupancy Rate"), rows);
    }

    private String conversionToCsv(ConversionReportResponse report) {
        List<List<String>> rows = new ArrayList<>();
        for (var item : report.items()) {
            rows.add(List.of(item.moduleType(), String.valueOf(item.totalInquiries()), String.valueOf(item.convertedCount()),
                    String.format("%.1f%%", item.conversionRate() * 100)));
        }
        rows.add(List.of(report.overall().moduleType(), String.valueOf(report.overall().totalInquiries()),
                String.valueOf(report.overall().convertedCount()), String.format("%.1f%%", report.overall().conversionRate() * 100)));
        return CsvWriter.write(List.of("Module Type", "Total Inquiries", "Converted", "Conversion Rate"), rows);
    }

    private static double rate(long numerator, long denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0.0;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }
}
