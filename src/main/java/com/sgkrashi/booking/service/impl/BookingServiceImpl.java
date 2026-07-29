package com.sgkrashi.booking.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.booking.dto.request.CancelBookingRequest;
import com.sgkrashi.booking.dto.request.CreateBookingRequest;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingLock;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.mapper.BookingMapper;
import com.sgkrashi.booking.repository.BookingLockRepository;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ConflictException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.equipmentrental.entity.Equipment;
import com.sgkrashi.equipmentrental.repository.EquipmentRepository;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Optional;
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
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);
    private static final String EQUIPMENT_OWNER_TYPE = "EQUIPMENT";
    /** Explicit IST anchor for the 48-hour cancellation cutoff, regardless of server locale/timezone. */
    private static final ZoneId BOOKING_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Duration FREE_CANCELLATION_WINDOW = Duration.ofHours(48);

    private record BookableItem(String name, BigDecimal rate, boolean available, String thumbnailUrl) {
    }

    private final BookingRepository bookingRepository;
    private final BookingLockRepository bookingLockRepository;
    private final EquipmentRepository equipmentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CurrentUserProvider currentUserProvider;
    private final BookingMapper bookingMapper;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            BookingLockRepository bookingLockRepository,
            EquipmentRepository equipmentRepository,
            MediaAssetRepository mediaAssetRepository,
            CurrentUserProvider currentUserProvider,
            BookingMapper bookingMapper
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingLockRepository = bookingLockRepository;
        this.equipmentRepository = equipmentRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.currentUserProvider = currentUserProvider;
        this.bookingMapper = bookingMapper;
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
            throw new BusinessRuleException("Bookings can only be cancelled more than 48 hours before the start date");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(request.reason());
        Booking saved = bookingRepository.save(booking);

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
    }

    private boolean isCancellable(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        Instant cutoff = booking.getStartDate().atStartOfDay(BOOKING_ZONE).toInstant().minus(FREE_CANCELLATION_WINDOW);
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

    /** Only EQUIPMENT exists today; STAY (Module 9) will extend this branch, not replace this method's shape. */
    private BookableItem resolveBookableItem(BookableType bookableType, Long bookableId) {
        if (bookableType == BookableType.EQUIPMENT) {
            Equipment equipment = equipmentRepository.findByIdAndIsActiveTrue(bookableId)
                    .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
            String thumbnailUrl = mediaAssetRepository
                    .findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(EQUIPMENT_OWNER_TYPE, equipment.getId())
                    .stream().findFirst().map(MediaAsset::getUrl).orElse(null);
            return new BookableItem(equipment.getName(), equipment.getDailyRate(), equipment.isAvailable(), thumbnailUrl);
        }
        throw new ResourceNotFoundException("Unsupported bookable type: " + bookableType);
    }

    private List<BookingResponse> buildResponses(List<Booking> bookings) {
        List<Long> equipmentIds = bookings.stream()
                .filter(booking -> booking.getBookableType() == BookableType.EQUIPMENT)
                .map(Booking::getBookableId)
                .distinct()
                .toList();

        Map<Long, Equipment> equipmentById = equipmentRepository.findAllById(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, equipment -> equipment));
        Map<Long, String> thumbnailsByEquipmentId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(EQUIPMENT_OWNER_TYPE, equipmentIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first, HashMap::new));

        return bookings.stream()
                .map(booking -> {
                    Equipment equipment = equipmentById.get(booking.getBookableId());
                    String name = equipment != null ? equipment.getName() : "Unknown item";
                    String thumbnailUrl = thumbnailsByEquipmentId.get(booking.getBookableId());
                    return bookingMapper.toResponse(booking, name, thumbnailUrl, isCancellable(booking));
                })
                .toList();
    }
}
