package com.sgkrashi.dairy.service;

import com.sgkrashi.analytics.util.CsvWriter;
import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.DuplicateResourceException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.dairy.dto.DairyDtos.AdminDeliveryRow;
import com.sgkrashi.dairy.dto.DairyDtos.AdminSubscriptionResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryAreaRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryAreaResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DeliverySlotRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DeliverySlotResponse;
import com.sgkrashi.dairy.entity.DairyDelivery;
import com.sgkrashi.dairy.entity.DairyDeliveryStatus;
import com.sgkrashi.dairy.entity.DairySubscription;
import com.sgkrashi.dairy.entity.DeliveryArea;
import com.sgkrashi.dairy.entity.DeliverySlot;
import com.sgkrashi.dairy.entity.SubscriptionStatus;
import com.sgkrashi.dairy.repository.DairyDeliveryRepository;
import com.sgkrashi.dairy.repository.DairySubscriptionRepository;
import com.sgkrashi.dairy.repository.DeliveryAreaRepository;
import com.sgkrashi.dairy.repository.DeliverySlotRepository;
import com.sgkrashi.dairy.service.DairySubscriptionEvent.Kind;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** Admin side of dairy: delivery areas and slots, the subscription list, and the daily delivery list. */
@Service
public class AdminDairyService {

    private static final String ENTITY_AREA = "DELIVERY_AREA";
    private static final String ENTITY_SLOT = "DELIVERY_SLOT";
    private static final String ENTITY_DELIVERY = "DAIRY_DELIVERY";

    private final DeliveryAreaRepository areaRepository;
    private final DeliverySlotRepository slotRepository;
    private final DairySubscriptionRepository subscriptionRepository;
    private final DairyDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    public AdminDairyService(
            DeliveryAreaRepository areaRepository,
            DeliverySlotRepository slotRepository,
            DairySubscriptionRepository subscriptionRepository,
            DairyDeliveryRepository deliveryRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher events,
            JdbcTemplate jdbc
    ) {
        this.areaRepository = areaRepository;
        this.slotRepository = slotRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.events = events;
        this.jdbc = jdbc;
    }

    // ---- delivery areas ----

    @Transactional(readOnly = true)
    public List<DeliveryAreaResponse> listAreas() {
        return areaRepository.findAllByOrderByPincodeAsc().stream().map(AdminDairyService::toResponse).toList();
    }

    @Transactional
    public DeliveryAreaResponse createArea(DeliveryAreaRequest request) {
        String pincode = request.pincode().trim();
        if (areaRepository.findByPincode(pincode).isPresent()) {
            throw new DuplicateResourceException("That pincode is already in the list.");
        }
        DeliveryArea area = new DeliveryArea();
        area.setPincode(pincode);
        area.setLabel(blankToNull(request.label()));
        area.setActive(request.isActive() == null || request.isActive());
        DeliveryAreaResponse after = toResponse(areaRepository.save(area));
        auditLogService.record(AuditActions.DELIVERY_AREA_CREATED, ENTITY_AREA, after.id(), null, after);
        return after;
    }

    @Transactional
    public DeliveryAreaResponse updateArea(Long id, DeliveryAreaRequest request) {
        DeliveryArea area = areaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Delivery area not found"));
        DeliveryAreaResponse before = toResponse(area);
        String pincode = request.pincode().trim();
        areaRepository.findByPincode(pincode).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new DuplicateResourceException("That pincode is already in the list.");
        });
        area.setPincode(pincode);
        area.setLabel(blankToNull(request.label()));
        if (request.isActive() != null) {
            area.setActive(request.isActive());
        }
        DeliveryAreaResponse after = toResponse(areaRepository.save(area));
        auditLogService.record(AuditActions.DELIVERY_AREA_UPDATED, ENTITY_AREA, id, before, after);
        return after;
    }

    @Transactional
    public void deactivateArea(Long id) {
        DeliveryArea area = areaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Delivery area not found"));
        DeliveryAreaResponse before = toResponse(area);
        area.setActive(false);
        auditLogService.record(AuditActions.DELIVERY_AREA_DEACTIVATED, ENTITY_AREA, id, before, toResponse(areaRepository.save(area)));
    }

    // ---- delivery slots ----

    @Transactional(readOnly = true)
    public List<DeliverySlotResponse> listSlots() {
        return slotRepository.findAllByOrderBySortOrderAscStartTimeAsc().stream().map(AdminDairyService::toResponse).toList();
    }

    @Transactional
    public DeliverySlotResponse createSlot(DeliverySlotRequest request) {
        DeliverySlot slot = new DeliverySlot();
        applySlot(slot, request);
        DeliverySlotResponse after = toResponse(slotRepository.save(slot));
        auditLogService.record(AuditActions.DELIVERY_SLOT_CREATED, ENTITY_SLOT, after.id(), null, after);
        return after;
    }

    @Transactional
    public DeliverySlotResponse updateSlot(Long id, DeliverySlotRequest request) {
        DeliverySlot slot = slotRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Delivery slot not found"));
        DeliverySlotResponse before = toResponse(slot);
        applySlot(slot, request);
        DeliverySlotResponse after = toResponse(slotRepository.save(slot));
        auditLogService.record(AuditActions.DELIVERY_SLOT_UPDATED, ENTITY_SLOT, id, before, after);
        return after;
    }

    @Transactional
    public void deactivateSlot(Long id) {
        DeliverySlot slot = slotRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Delivery slot not found"));
        DeliverySlotResponse before = toResponse(slot);
        slot.setActive(false);
        auditLogService.record(AuditActions.DELIVERY_SLOT_DEACTIVATED, ENTITY_SLOT, id, before, toResponse(slotRepository.save(slot)));
    }

    private void applySlot(DeliverySlot slot, DeliverySlotRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException("The slot must end after it starts.");
        }
        slot.setName(request.name().trim());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setDays(request.days());
        slot.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        if (request.isActive() != null) {
            slot.setActive(request.isActive());
        }
    }

    // ---- subscriptions ----

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminSubscriptionResponse> listSubscriptions(SubscriptionStatus status, Long userId, Long productId, int page, int size) {
        Page<DairySubscription> result = subscriptionRepository.search(status, userId, productId,
                PageRequest.of(Math.max(page, 0), size > 0 ? Math.min(size, 100) : 20));
        List<DairySubscription> subscriptions = result.getContent();
        Map<Long, User> users = userRepository.findAllById(subscriptions.stream().map(DairySubscription::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> productNames = new java.util.HashMap<>();
        Map<Long, String> slotNames = slotRepository.findAll().stream().collect(Collectors.toMap(DeliverySlot::getId, DeliverySlot::getName));
        for (DairySubscription s : subscriptions) {
            productNames.computeIfAbsent(s.getProductId(), pid -> jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, pid));
        }
        LocalDate today = LocalDate.now(com.sgkrashi.dairy.config.DairyTime.IST);
        List<AdminSubscriptionResponse> items = new ArrayList<>();
        for (DairySubscription s : subscriptions) {
            User user = users.get(s.getUserId());
            items.add(new AdminSubscriptionResponse(
                    s.getId(), s.getUserId(),
                    user == null ? null : user.getName(), user == null ? null : user.getEmail(), user == null ? null : user.getPhone(),
                    s.getProductId(), productNames.get(s.getProductId()), s.getQuantity(), s.getFrequency(),
                    s.weekdaySet().stream().sorted().toList(), s.getStartDate(),
                    s.getSlotId() == null ? null : slotNames.get(s.getSlotId()),
                    s.effectiveStatus(today).name(), s.getPauseFrom(), s.getPauseTo(), s.getAddressPincode(), s.getCreatedAt()));
        }
        return PaginatedResponse.of(items, result);
    }

    // ---- daily delivery list ----

    @Transactional(readOnly = true)
    public List<AdminDeliveryRow> deliveriesForDate(LocalDate date, Long slotId, DairyDeliveryStatus status) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.subscription_id, d.delivery_date, d.product_name, d.quantity, d.slot_name, d.slot_start, d.slot_end,
                       u.name, u.phone, d.address_line1, d.address_line2, d.address_city, d.address_state, d.address_pincode,
                       d.line_total, d.status, d.cash_collected, d.note
                FROM dairy_deliveries d
                JOIN dairy_subscriptions s ON s.id = d.subscription_id
                JOIN users u ON u.id = s.user_id
                WHERE d.delivery_date = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(date));
        if (slotId != null) {
            sql.append(" AND s.slot_id = ?");
            args.add(slotId);
        }
        if (status != null) {
            sql.append(" AND d.status = ?");
            args.add(status.name());
        }
        sql.append(" ORDER BY d.slot_start, d.address_pincode, d.id");
        return jdbc.query(sql.toString(), (rs, i) -> new AdminDeliveryRow(
                rs.getLong(1), rs.getLong(2), rs.getDate(3).toLocalDate(), rs.getString(4), rs.getInt(5), rs.getString(6),
                toLocalTime(rs.getTime(7)), toLocalTime(rs.getTime(8)), rs.getString(9), rs.getString(10),
                rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15),
                rs.getBigDecimal(16), rs.getString(17), rs.getBoolean(18), rs.getString(19)), args.toArray());
    }

    /** The day's list as CSV for the delivery team. Customer details are only what a rider needs. */
    @Transactional(readOnly = true)
    public String deliveriesCsv(LocalDate date, Long slotId, DairyDeliveryStatus status) {
        List<String> headers = List.of("Date", "Slot", "Start", "End", "Customer", "Phone", "Address line 1", "Address line 2",
                "City", "Pincode", "Product", "Quantity", "Amount due", "Status", "Cash collected", "Note");
        List<List<String>> rows = new ArrayList<>();
        for (AdminDeliveryRow r : deliveriesForDate(date, slotId, status)) {
            rows.add(Stream.of(
                    String.valueOf(r.date()), nullToEmpty(r.slotName()), nullToEmpty(r.slotStart()),
                    nullToEmpty(r.slotEnd()), nullToEmpty(r.customerName()), nullToEmpty(r.customerPhone()),
                    nullToEmpty(r.addressLine1()), nullToEmpty(r.addressLine2()), nullToEmpty(r.addressCity()), nullToEmpty(r.addressPincode()),
                    nullToEmpty(r.productName()), String.valueOf(r.quantity()), r.lineTotal().toPlainString(), r.status(),
                    r.cashCollected() ? "yes" : "no", nullToEmpty(r.note())).map(AdminDairyService::safeCell).toList());
        }
        return CsvWriter.write(headers, rows);
    }

    @Transactional
    public AdminDeliveryRow markDelivered(Long deliveryId, Boolean cashCollected) {
        DairyDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
        requireScheduled(delivery);
        DairyDeliveryStatus before = delivery.getStatus();
        delivery.setStatus(DairyDeliveryStatus.DELIVERED);
        delivery.setCashCollected(cashCollected == null || cashCollected);
        deliveryRepository.save(delivery);
        publish(delivery, Kind.DELIVERED, null);
        auditLogService.record(AuditActions.DAIRY_DELIVERY_DELIVERED, ENTITY_DELIVERY, deliveryId,
                Map.of("status", before.name()), Map.of("status", delivery.getStatus().name(), "cashCollected", delivery.isCashCollected()));
        return rowFor(delivery);
    }

    @Transactional
    public AdminDeliveryRow markFailed(Long deliveryId, String reason) {
        DairyDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
        requireScheduled(delivery);
        DairyDeliveryStatus before = delivery.getStatus();
        delivery.setStatus(DairyDeliveryStatus.FAILED);
        delivery.setNote(blankToNull(reason));
        deliveryRepository.save(delivery);
        publish(delivery, Kind.DELIVERY_FAILED, delivery.getNote());
        auditLogService.record(AuditActions.DAIRY_DELIVERY_FAILED, ENTITY_DELIVERY, deliveryId,
                Map.of("status", before.name()), Map.of("status", delivery.getStatus().name(), "reason", nullToEmpty(delivery.getNote())));
        return rowFor(delivery);
    }

    private void requireScheduled(DairyDelivery delivery) {
        if (delivery.getStatus() != DairyDeliveryStatus.SCHEDULED) {
            throw new BusinessRuleException("Only a scheduled delivery can be marked. This one is " + delivery.getStatus() + ".");
        }
    }

    private void publish(DairyDelivery delivery, Kind kind, String detail) {
        Long userId = subscriptionRepository.findById(delivery.getSubscriptionId()).map(DairySubscription::getUserId).orElse(null);
        if (userId != null) {
            events.publishEvent(new DairySubscriptionEvent(kind, userId, delivery.getSubscriptionId(), delivery.getProductName(),
                    delivery.getDeliveryDate(), detail));
        }
    }

    private AdminDeliveryRow rowFor(DairyDelivery d) {
        return deliveriesForDate(d.getDeliveryDate(), null, null).stream().filter(r -> r.id().equals(d.getId())).findFirst().orElse(null);
    }

    // ---- small helpers ----

    /**
     * A cell that starts with = + - @ would be run as a formula by a spreadsheet, and names and addresses come from
     * customers. A leading apostrophe stops that; plain phone numbers (which may start with +) are left as they are.
     */
    static String safeCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.matches("^\\+?[0-9][0-9 \\-]*$")) {
            return value;
        }
        char first = value.charAt(0);
        boolean risky = first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
        return risky ? "'" + value : value;
    }

    private static LocalTime toLocalTime(Time time) {
        return time == null ? null : time.toLocalTime();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static DeliveryAreaResponse toResponse(DeliveryArea area) {
        return new DeliveryAreaResponse(area.getId(), area.getPincode(), area.getLabel(), area.isActive());
    }

    static DeliverySlotResponse toResponse(DeliverySlot slot) {
        return new DeliverySlotResponse(slot.getId(), slot.getName(), slot.getStartTime(), slot.getEndTime(),
                slot.days().stream().sorted().toList(), slot.getSortOrder(), slot.isActive());
    }
}
