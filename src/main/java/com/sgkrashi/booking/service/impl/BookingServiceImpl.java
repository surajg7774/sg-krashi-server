package com.sgkrashi.booking.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.booking.dto.request.CancelBookingRequest;
import com.sgkrashi.booking.dto.request.CreateBookingRequest;
import com.sgkrashi.booking.dto.response.AdminBookingResponse;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingLock;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.mapper.BookingMapper;
import com.sgkrashi.booking.policy.CancellationPolicy;
import com.sgkrashi.booking.repository.BookingLockRepository;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.booking.specification.BookingSpecifications;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ConflictException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.equipmentrental.entity.Equipment;
import com.sgkrashi.equipmentrental.repository.EquipmentRepository;
import com.sgkrashi.farmstay.entity.StayListing;
import com.sgkrashi.farmstay.repository.StayListingRepository;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.notification.event.BookingCancelledEvent;
import com.sgkrashi.notification.event.BookingConfirmedEvent;
import com.sgkrashi.notification.event.PaymentFailedEvent;
import com.sgkrashi.notification.event.RefundProcessedEvent;
import com.sgkrashi.payment.entity.Payment;
import com.sgkrashi.payment.entity.PaymentStatus;
import com.sgkrashi.payment.repository.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <h2>Overlap prevention — the critical correctness logic of this module</h2>
 *
 * <p><b>Locking strategy (deliberately NOT a copy of Module 6/7's product/crop
 * stock locking):</b> Module 6/7 locked the row that itself carried the
 * contended quantity ({@code Product.stock_qty}, {@code CropListing.
 * quantity_available}) and decremented it under that same lock. A booking has
 * no such row — {@code Equipment} carries no "how many date-ranges are left"
 * counter, and the generic {@code Booking} engine is deliberately built to
 * know nothing about Equipment/StayListing at all (per this module's own
 * brief: Module 9 must be able to reuse this engine untouched). So instead of
 * locking the bookable resource's own row, every {@code (bookableType,
 * bookableId)} pair gets its own {@link BookingLock} anchor row, created
 * on demand, purely to serialize concurrent booking attempts against the same
 * bookable item — see {@code BookingLock}'s Javadoc. Acquiring that lock (via
 * {@code SELECT ... FOR UPDATE}) BEFORE running the overlap query serializes
 * WHEN two concurrent attempts run — but that alone is NOT sufficient, and an
 * earlier version of this method that stopped there had a real double-booking
 * bug, caught by the concurrency test described below. See
 * {@code BookingRepository.findOverlappingForUpdate}'s Javadoc for the second
 * half of the fix: the overlap query itself must ALSO be a locking read, or a
 * transaction that correctly waited its turn can still consult its own stale
 * REPEATABLE READ snapshot and miss the winner's already-committed row.
 *
 * <p>Only one lock is ever acquired per {@code createBooking} call (a single
 * bookable item per booking), unlike Module 6/7's checkout which locks
 * multiple product/crop rows and therefore needed a sorted acquisition order
 * to avoid deadlocks — that concern doesn't apply here.
 *
 * <p><b>Module 6's eager-fetch-shadowing lesson, applied:</b> that bug was
 * Hibernate returning an already-attached (pre-lock) entity instead of the
 * freshly-locked row's data, because the SAME entity had been eagerly loaded
 * earlier in the same persistence context. That risk requires re-fetching the
 * SAME row under lock after an earlier unlocked read of it. This method never
 * does that: {@link Equipment} is read once (unlocked, for its name/rate/
 * availability) and never re-fetched, because nothing here locks or mutates
 * the Equipment row itself — the lock target ({@link BookingLock}) is a
 * completely different row. So there is no shadowing risk to guard against in
 * this flow; this is called out explicitly because the prompt asked for it to
 * be considered, not because it applies here.
 *
 * <p><b>Date-range convention:</b> see {@code Booking}'s Javadoc — start
 * inclusive, end exclusive. Overlap between an existing range
 * {@code [existingStart, existingEnd)} and the requested
 * {@code [requestStart, requestEnd)} is {@code existingStart < requestEnd AND
 * requestStart < existingEnd} (strict {@code <}, not {@code <=} — back-to-back
 * bookings, e.g. one ending Thursday and another starting that same Thursday,
 * do NOT conflict).
 *
 * <p><b>Module 9 addendum (Farm Stay):</b> confirms the claim above still
 * holds — {@code acquireBookingLock}, {@code findOverlappingForUpdate}, and
 * every line of the overlap-prevention transaction are byte-for-byte
 * unchanged. What Module 9 DID add, all outside that core logic: a
 * {@code STAY} branch in {@link #resolveBookableItem} (this method's own
 * Javadoc already anticipated exactly this extension), an optional
 * guest-count guard next to the existing availability check in
 * {@link #createBooking}, and a per-{@code BookableType} cancellation window
 * ({@link CancellationPolicy}) replacing what was a hardcoded 48-hour
 * constant — Farm Stay needs a stricter 7-day window, and Module 8 had no
 * second bookable type to prove that needed to be configurable yet.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);
    private static final String EQUIPMENT_OWNER_TYPE = "EQUIPMENT";
    private static final String STAY_OWNER_TYPE = "STAY";
    private static final String PAYABLE_TYPE_BOOKING = "BOOKING";
    private static final String ENTITY_TYPE_BOOKING = "BOOKING";
    /** Explicit IST anchor for cancellation cutoffs, regardless of server locale/timezone. */
    private static final ZoneId BOOKING_ZONE = ZoneId.of("Asia/Kolkata");

    private record BookableItem(String name, BigDecimal rate, boolean available, String thumbnailUrl, Integer maxGuests) {
    }

    private final BookingRepository bookingRepository;
    private final BookingLockRepository bookingLockRepository;
    private final EquipmentRepository equipmentRepository;
    private final StayListingRepository stayListingRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            BookingLockRepository bookingLockRepository,
            EquipmentRepository equipmentRepository,
            StayListingRepository stayListingRepository,
            MediaAssetRepository mediaAssetRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            CurrentUserProvider currentUserProvider,
            BookingMapper bookingMapper,
            ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingLockRepository = bookingLockRepository;
        this.equipmentRepository = equipmentRepository;
        this.stayListingRepository = stayListingRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserProvider = currentUserProvider;
        this.bookingMapper = bookingMapper;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessRuleException("End date must be after start date");
        }

        Long userId = currentUserProvider.getCurrentUserId();
        BookableItem item = resolveBookableItem(request.bookableType(), request.bookableId());
        if (!item.available()) {
            throw new BusinessRuleException("\"" + item.name() + "\" is not currently available for booking");
        }
        // Generic-shaped on purpose: "if this bookable item caps guests and the
        // request specifies a count, enforce it" — doesn't hardcode STAY
        // reasoning, just compares two optional numbers next to the existing
        // availability check above.
        if (item.maxGuests() != null && request.guestCount() != null && request.guestCount() > item.maxGuests()) {
            throw new BusinessRuleException(
                    "\"" + item.name() + "\" accommodates at most " + item.maxGuests() + " guest(s)");
        }

        acquireBookingLock(request.bookableType(), request.bookableId());

        // findOverlappingForUpdate (not findOverlapping): a plain read here would
        // still be answered from this transaction's own REPEATABLE READ snapshot,
        // taken before this request even started — see that method's Javadoc for
        // how a concurrency test caught this exact gap.
        List<Booking> overlapping = bookingRepository.findOverlappingForUpdate(
                request.bookableType(), request.bookableId(), BLOCKING_STATUSES, request.startDate(), request.endDate());
        if (!overlapping.isEmpty()) {
            throw new ConflictException("\"" + item.name() + "\" is already booked for part of that date range");
        }

        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        BigDecimal totalPrice = item.rate().multiply(BigDecimal.valueOf(days));

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setBookableType(request.bookableType());
        booking.setBookableId(request.bookableId());
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalPrice(totalPrice);
        booking.setGuestCount(request.guestCount());
        Booking saved = bookingRepository.save(booking);

        return bookingMapper.toResponse(saved, item.name(), item.thumbnailUrl(), isCancellable(saved));
    }

    /**
     * Gets or creates the lock anchor row for this bookable item and locks it
     * for the rest of the caller's transaction. The create path has its own
     * narrow race (two concurrent FIRST-ever bookings of the same item both
     * finding no lock row) — the {@code uq_booking_locks_bookable} constraint
     * makes the loser's insert fail, at which point it just re-fetches under
     * lock, which blocks until the winner commits, then proceeds normally. In
     * practice this module pre-seeds a lock row per seeded Equipment item, so
     * this path is defensive rather than load-bearing today.
     */
    private void acquireBookingLock(BookableType bookableType, Long bookableId) {
        if (bookingLockRepository.findForUpdate(bookableType, bookableId).isPresent()) {
            return;
        }
        try {
            BookingLock lock = new BookingLock();
            lock.setBookableType(bookableType);
            lock.setBookableId(bookableId);
            bookingLockRepository.saveAndFlush(lock);
        } catch (DataIntegrityViolationException raceLost) {
            bookingLockRepository.findForUpdate(bookableType, bookableId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Booking lock row missing after unique constraint violation for "
                                    + bookableType + "/" + bookableId));
        }
    }

    @Override
    public PaginatedResponse<BookingResponse> listMyBookings(int page, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        Page<Booking> bookingsPage = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<BookingResponse> items = buildResponses(bookingsPage.getContent());
        return PaginatedResponse.of(items, bookingsPage);
    }

    @Override
    public BookingResponse getBookingDetail(Long bookingId) {
        Booking booking = getOwnedBookingOrThrow(bookingId);
        BookableItem item = resolveBookableItem(booking.getBookableType(), booking.getBookableId());
        return bookingMapper.toResponse(booking, item.name(), item.thumbnailUrl(), isCancellable(booking));
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, CancelBookingRequest request) {
        Booking booking = getOwnedBookingOrThrow(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessRuleException("This booking can no longer be cancelled");
        }
        if (!isCancellable(booking)) {
            String label = CancellationPolicy.forType(booking.getBookableType()).windowLabel();
            throw new BusinessRuleException("Bookings can only be cancelled more than " + label + " before the start date");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(request.reason());
        Booking saved = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingCancelledEvent(saved.getId(), saved.getUserId(), saved.getCancellationReason()));

        BookableItem item = resolveBookableItem(saved.getBookableType(), saved.getBookableId());
        return bookingMapper.toResponse(saved, item.name(), item.thumbnailUrl(), false);
    }

    @Override
    public Booking getBookingEntityOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Override
    @Transactional
    public void markConfirmed(Long bookingId) {
        Booking booking = getBookingEntityOrThrow(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingConfirmedEvent(booking.getId(), booking.getUserId()));
    }

    @Override
    @Transactional
    public void markPaymentFailed(Long bookingId) {
        Booking booking = getBookingEntityOrThrow(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason("Payment failed");
        bookingRepository.save(booking);
        eventPublisher.publishEvent(new PaymentFailedEvent("BOOKING", booking.getId(), booking.getUserId()));
    }

    /**
     * Called only from {@code RefundServiceImpl}, already guarded against
     * calling this twice for the same refund. Leaves {@code cancelledAt}/
     * {@code cancellationReason} untouched if the booking was already
     * CANCELLED for an unrelated reason — see this method's Javadoc on
     * {@code BookingService}.
     */
    @Override
    @Transactional
    public void markRefunded(Long bookingId) {
        Booking booking = getBookingEntityOrThrow(bookingId);
        boolean wasAlreadyCancelled = booking.getStatus() == BookingStatus.CANCELLED;
        if (!wasAlreadyCancelled) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(Instant.now());
            booking.setCancellationReason("Refunded by admin");
            bookingRepository.save(booking);
        }
        eventPublisher.publishEvent(new RefundProcessedEvent(PAYABLE_TYPE_BOOKING, booking.getId(), booking.getUserId(), booking.getTotalPrice()));
    }

    @Override
    public PaginatedResponse<AdminBookingResponse> listBookingsForAdmin(
            BookingStatus status, Long userId, Instant dateFrom, Instant dateTo, int page, int size
    ) {
        Specification<Booking> spec = Specification.allOf(
                BookingSpecifications.hasStatus(status),
                BookingSpecifications.hasUserId(userId),
                BookingSpecifications.createdAfter(dateFrom),
                BookingSpecifications.createdBefore(dateTo));

        Page<Booking> bookingsPage = bookingRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by("createdAt").descending()));
        List<Booking> bookings = bookingsPage.getContent();

        List<Long> userIds = bookings.stream().map(Booking::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, Payment> paymentsByBookingId = paymentRepository.findByPayableTypeAndPayableIdIn(PAYABLE_TYPE_BOOKING, bookingIds).stream()
                .collect(Collectors.toMap(Payment::getPayableId, p -> p));

        List<AdminBookingResponse> items = buildAdminResponses(bookings, usersById, paymentsByBookingId);
        return PaginatedResponse.of(items, bookingsPage);
    }

    @Override
    public AdminBookingResponse getBookingDetailForAdmin(Long bookingId) {
        Booking booking = getBookingEntityOrThrow(bookingId);
        User user = userRepository.findById(booking.getUserId()).orElse(null);
        Payment payment = paymentRepository.findByPayableTypeAndPayableId(PAYABLE_TYPE_BOOKING, bookingId).orElse(null);
        boolean refunded = payment != null && payment.getStatus() == PaymentStatus.REFUNDED;

        BookableItem item = resolveBookableItem(booking.getBookableType(), booking.getBookableId());
        return bookingMapper.toAdminResponse(
                booking, item.name(), item.thumbnailUrl(),
                user != null ? user.getName() : "Unknown",
                user != null ? user.getEmail() : null,
                refunded, payment != null ? payment.getRefundedAt() : null);
    }

    /**
     * Bypasses {@code isCancellable}'s free-cancellation-window check entirely
     * — this is an admin override, not the customer self-service path (see
     * {@link #cancelBooking}). CANCELLED and COMPLETED are terminal: once
     * reached, no further admin status change is accepted (only notes).
     */
    @Override
    @Transactional
    public AdminBookingResponse updateBookingStatus(Long bookingId, BookingStatus newStatus, String adminNotes) {
        Booking booking = getBookingEntityOrThrow(bookingId);
        boolean isTerminal = booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED;
        if (isTerminal && newStatus != booking.getStatus()) {
            throw new BusinessRuleException("This booking's status is final and cannot be changed");
        }

        AdminBookingResponse before = getBookingDetailForAdmin(bookingId);
        booking.setAdminNotes(adminNotes);
        if (newStatus != booking.getStatus()) {
            booking.setStatus(newStatus);
            if (newStatus == BookingStatus.CANCELLED) {
                booking.setCancelledAt(Instant.now());
                booking.setCancellationReason("Cancelled by admin");
            }
        }
        Booking saved = bookingRepository.save(booking);
        BookableItem item = resolveBookableItem(saved.getBookableType(), saved.getBookableId());
        User user = userRepository.findById(saved.getUserId()).orElse(null);
        Payment payment = paymentRepository.findByPayableTypeAndPayableId(PAYABLE_TYPE_BOOKING, bookingId).orElse(null);
        boolean refunded = payment != null && payment.getStatus() == PaymentStatus.REFUNDED;
        AdminBookingResponse after = bookingMapper.toAdminResponse(
                saved, item.name(), item.thumbnailUrl(),
                user != null ? user.getName() : "Unknown",
                user != null ? user.getEmail() : null,
                refunded, payment != null ? payment.getRefundedAt() : null);
        auditLogService.record(AuditActions.BOOKING_STATUS_UPDATED, ENTITY_TYPE_BOOKING, bookingId, before, after);
        return after;
    }

    private List<AdminBookingResponse> buildAdminResponses(List<Booking> bookings, Map<Long, User> usersById, Map<Long, Payment> paymentsByBookingId) {
        List<Long> equipmentIds = bookings.stream()
                .filter(booking -> booking.getBookableType() == BookableType.EQUIPMENT)
                .map(Booking::getBookableId)
                .distinct()
                .toList();
        List<Long> stayIds = bookings.stream()
                .filter(booking -> booking.getBookableType() == BookableType.STAY)
                .map(Booking::getBookableId)
                .distinct()
                .toList();

        Map<Long, Equipment> equipmentById = equipmentRepository.findAllById(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, equipment -> equipment));
        Map<Long, StayListing> stayById = stayListingRepository.findAllById(stayIds).stream()
                .collect(Collectors.toMap(StayListing::getId, stay -> stay));

        Map<Long, String> thumbnailsByEquipmentId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(EQUIPMENT_OWNER_TYPE, equipmentIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first, HashMap::new));
        Map<Long, String> thumbnailsByStayId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(STAY_OWNER_TYPE, stayIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first, HashMap::new));

        return bookings.stream()
                .map(booking -> {
                    String name;
                    String thumbnailUrl;
                    if (booking.getBookableType() == BookableType.EQUIPMENT) {
                        Equipment equipment = equipmentById.get(booking.getBookableId());
                        name = equipment != null ? equipment.getName() : "Unknown item";
                        thumbnailUrl = thumbnailsByEquipmentId.get(booking.getBookableId());
                    } else {
                        StayListing stay = stayById.get(booking.getBookableId());
                        name = stay != null ? stay.getName() : "Unknown item";
                        thumbnailUrl = thumbnailsByStayId.get(booking.getBookableId());
                    }
                    User user = usersById.get(booking.getUserId());
                    Payment payment = paymentsByBookingId.get(booking.getId());
                    boolean refunded = payment != null && payment.getStatus() == PaymentStatus.REFUNDED;
                    return bookingMapper.toAdminResponse(
                            booking, name, thumbnailUrl,
                            user != null ? user.getName() : "Unknown",
                            user != null ? user.getEmail() : null,
                            refunded, payment != null ? payment.getRefundedAt() : null);
                })
                .toList();
    }

    private boolean isCancellable(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        Duration window = CancellationPolicy.forType(booking.getBookableType()).freeWindow();
        Instant cutoff = booking.getStartDate().atStartOfDay(BOOKING_ZONE).toInstant().minus(window);
        return Instant.now().isBefore(cutoff);
    }

    private Booking getOwnedBookingOrThrow(Long bookingId) {
        Booking booking = getBookingEntityOrThrow(bookingId);
        Long userId = currentUserProvider.getCurrentUserId();
        if (!booking.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Booking not found");
        }
        return booking;
    }

    /**
     * EQUIPMENT (Module 8) and STAY (Module 9) branches — exactly the
     * extension this method's original Javadoc anticipated, not a change to
     * the class's overlap-prevention/locking logic above.
     */
    private BookableItem resolveBookableItem(BookableType bookableType, Long bookableId) {
        if (bookableType == BookableType.EQUIPMENT) {
            Equipment equipment = equipmentRepository.findByIdAndIsActiveTrue(bookableId)
                    .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
            String thumbnailUrl = firstThumbnail(EQUIPMENT_OWNER_TYPE, equipment.getId());
            return new BookableItem(equipment.getName(), equipment.getDailyRate(), equipment.isAvailable(), thumbnailUrl, null);
        }
        if (bookableType == BookableType.STAY) {
            StayListing stay = stayListingRepository.findByIdAndIsActiveTrue(bookableId)
                    .orElseThrow(() -> new ResourceNotFoundException("Stay listing not found"));
            String thumbnailUrl = firstThumbnail(STAY_OWNER_TYPE, stay.getId());
            return new BookableItem(stay.getName(), stay.getNightlyRate(), stay.isAvailable(), thumbnailUrl, stay.getMaxGuests());
        }
        throw new ResourceNotFoundException("Unsupported bookable type: " + bookableType);
    }

    private String firstThumbnail(String ownerType, Long ownerId) {
        return mediaAssetRepository.findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(ownerType, ownerId)
                .stream().findFirst().map(MediaAsset::getUrl).orElse(null);
    }

    private List<BookingResponse> buildResponses(List<Booking> bookings) {
        List<Long> equipmentIds = bookings.stream()
                .filter(booking -> booking.getBookableType() == BookableType.EQUIPMENT)
                .map(Booking::getBookableId)
                .distinct()
                .toList();
        List<Long> stayIds = bookings.stream()
                .filter(booking -> booking.getBookableType() == BookableType.STAY)
                .map(Booking::getBookableId)
                .distinct()
                .toList();

        Map<Long, Equipment> equipmentById = equipmentRepository.findAllById(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, equipment -> equipment));
        Map<Long, StayListing> stayById = stayListingRepository.findAllById(stayIds).stream()
                .collect(Collectors.toMap(StayListing::getId, stay -> stay));

        Map<Long, String> thumbnailsByEquipmentId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(EQUIPMENT_OWNER_TYPE, equipmentIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first, HashMap::new));
        Map<Long, String> thumbnailsByStayId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(STAY_OWNER_TYPE, stayIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first, HashMap::new));

        return bookings.stream()
                .map(booking -> {
                    String name;
                    String thumbnailUrl;
                    if (booking.getBookableType() == BookableType.EQUIPMENT) {
                        Equipment equipment = equipmentById.get(booking.getBookableId());
                        name = equipment != null ? equipment.getName() : "Unknown item";
                        thumbnailUrl = thumbnailsByEquipmentId.get(booking.getBookableId());
                    } else {
                        StayListing stay = stayById.get(booking.getBookableId());
                        name = stay != null ? stay.getName() : "Unknown item";
                        thumbnailUrl = thumbnailsByStayId.get(booking.getBookableId());
                    }
                    return bookingMapper.toResponse(booking, name, thumbnailUrl, isCancellable(booking));
                })
                .toList();
    }
}
