package com.sgkrashi.dairy.service;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.customer.entity.Address;
import com.sgkrashi.customer.repository.AddressRepository;
import com.sgkrashi.dairy.config.DairyProperties;
import com.sgkrashi.dairy.config.DairyTime;
import com.sgkrashi.dairy.dto.DairyDtos.CreateSubscriptionRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryHistoryItem;
import com.sgkrashi.dairy.dto.DairyDtos.PauseRequest;
import com.sgkrashi.dairy.dto.DairyDtos.SubscriptionResponse;
import com.sgkrashi.dairy.dto.DairyDtos.UpcomingDelivery;
import com.sgkrashi.dairy.entity.DairyDelivery;
import com.sgkrashi.dairy.entity.DairyDeliveryStatus;
import com.sgkrashi.dairy.entity.DairySubscription;
import com.sgkrashi.dairy.entity.DeliverySlot;
import com.sgkrashi.dairy.entity.SubscriptionFrequency;
import com.sgkrashi.dairy.entity.SubscriptionStatus;
import com.sgkrashi.dairy.repository.DairyDeliveryRepository;
import com.sgkrashi.dairy.repository.DairySubscriptionRepository;
import com.sgkrashi.dairy.repository.DeliverySlotRepository;
import com.sgkrashi.dairy.service.DairySubscriptionEvent.Kind;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a customer can do with their own subscriptions. Another customer's subscription is a 404, never a 403, like the
 * rest of the API (it must not reveal that the id exists).
 *
 * <p>Payment for a subscription delivery is taken at the door, so nothing here touches the order or payment code.
 */
@Service
public class DairySubscriptionService {

    /** How many days ahead the "upcoming deliveries" list looks. */
    static final int UPCOMING_DAYS = 14;

    private final DairySubscriptionRepository subscriptionRepository;
    private final DairyDeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;
    private final DeliverySlotRepository slotRepository;
    private final AddressRepository addressRepository;
    private final DairyCatalogService catalogService;
    private final DeliveryRulesService rulesService;
    private final DairyStockService stockService;
    private final DairyProperties properties;
    private final DairyTime time;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher events;

    public DairySubscriptionService(
            DairySubscriptionRepository subscriptionRepository,
            DairyDeliveryRepository deliveryRepository,
            ProductRepository productRepository,
            DeliverySlotRepository slotRepository,
            AddressRepository addressRepository,
            DairyCatalogService catalogService,
            DeliveryRulesService rulesService,
            DairyStockService stockService,
            DairyProperties properties,
            DairyTime time,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher events
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.productRepository = productRepository;
        this.slotRepository = slotRepository;
        this.addressRepository = addressRepository;
        this.catalogService = catalogService;
        this.rulesService = rulesService;
        this.stockService = stockService;
        this.properties = properties;
        this.time = time;
        this.currentUserProvider = currentUserProvider;
        this.events = events;
    }

    @Transactional
    public SubscriptionResponse create(CreateSubscriptionRequest request) {
        if (!properties.subscriptionsEnabled()) {
            throw new BusinessRuleException("Dairy subscriptions are not available yet.");
        }
        Long userId = currentUserProvider.getCurrentUserId();

        Product product = productRepository.findByIdAndIsActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!catalogService.isDairy(product)) {
            throw new BusinessRuleException("Only dairy products can be subscribed to.");
        }
        if (request.quantity() > properties.maxQuantity()) {
            throw new BusinessRuleException("You can subscribe to at most " + properties.maxQuantity() + " packs per delivery.");
        }

        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (request.frequency() == SubscriptionFrequency.DAILY) {
            days.addAll(EnumSet.allOf(DayOfWeek.class));
        } else if (request.weekdays() != null) {
            days.addAll(request.weekdays());
        }
        if (days.isEmpty()) {
            throw new BusinessRuleException("Choose at least one delivery day.");
        }

        LocalDate earliest = time.today().plusDays(Math.max(1, properties.minLeadDays()));
        if (request.startDate().isBefore(earliest)) {
            throw new BusinessRuleException("The first delivery can be on " + earliest + " at the earliest.");
        }

        Address address = addressRepository.findById(request.addressId())
                .filter(Address::isActive)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!rulesService.serves(address.getPincode())) {
            throw new BusinessRuleException("Sorry, we don't deliver dairy to pincode " + address.getPincode() + " yet.");
        }

        DeliverySlot slot = resolveSlot(request.slotId(), days);

        DairySubscription subscription = new DairySubscription();
        subscription.setUserId(userId);
        subscription.setProductId(product.getId());
        subscription.setQuantity(request.quantity());
        subscription.setFrequency(request.frequency());
        subscription.setWeekdaySet(request.frequency() == SubscriptionFrequency.DAYS ? days : null);
        subscription.setStartDate(request.startDate());
        subscription.setSlotId(slot == null ? null : slot.getId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAddressLine1(address.getLine1());
        subscription.setAddressLine2(address.getLine2());
        subscription.setAddressCity(address.getCity());
        subscription.setAddressState(address.getState());
        subscription.setAddressPincode(address.getPincode());
        DairySubscription saved = subscriptionRepository.save(subscription);

        events.publishEvent(new DairySubscriptionEvent(Kind.CREATED, userId, saved.getId(), product.getName(), saved.getStartDate(), null));
        return toResponse(saved, product, slot, true);
    }

    /** With slots configured a slot is required and must run on every chosen day; with none configured there is nothing to pick. */
    private DeliverySlot resolveSlot(Long slotId, Set<DayOfWeek> days) {
        List<DeliverySlot> slots = rulesService.activeSlots();
        if (slots.isEmpty()) {
            return null;
        }
        if (slotId == null) {
            throw new BusinessRuleException("Please choose a delivery slot.");
        }
        DeliverySlot slot = slots.stream().filter(s -> s.getId().equals(slotId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("That delivery slot is not available. Please choose another."));
        for (DayOfWeek day : days) {
            if (!slot.deliversOn(day)) {
                throw new BusinessRuleException("The \"" + slot.getName() + "\" slot doesn't deliver on " + day.name().charAt(0)
                        + day.name().substring(1).toLowerCase() + "s.");
            }
        }
        return slot;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listMine() {
        Long userId = currentUserProvider.getCurrentUserId();
        List<DairySubscription> subscriptions = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Product> products = productsById(subscriptions);
        Map<Long, DeliverySlot> slots = slotsById(subscriptions);
        return subscriptions.stream()
                .map(s -> toResponse(s, products.get(s.getProductId()), slots.get(s.getSlotId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getMine(Long id) {
        DairySubscription subscription = ownedOrThrow(id);
        return toResponse(subscription, productRepository.findById(subscription.getProductId()).orElse(null),
                subscription.getSlotId() == null ? null : slotRepository.findById(subscription.getSlotId()).orElse(null), true);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<DeliveryHistoryItem> history(Long id, int page, int size) {
        DairySubscription subscription = ownedOrThrow(id);
        Page<DairyDelivery> deliveries = deliveryRepository.findBySubscriptionIdOrderByDeliveryDateDesc(
                subscription.getId(), PageRequest.of(Math.max(page, 0), size > 0 ? Math.min(size, 100) : 20));
        List<DeliveryHistoryItem> items = deliveries.getContent().stream()
                .map(d -> new DeliveryHistoryItem(d.getId(), d.getDeliveryDate(), d.getQuantity(), d.getProductName(),
                        d.getLineTotal(), d.getStatus().name(), d.isCashCollected()))
                .toList();
        return PaginatedResponse.of(items, deliveries);
    }

    @Transactional
    public SubscriptionResponse pause(Long id, PauseRequest request) {
        DairySubscription subscription = ownedForUpdateOrThrow(id);
        requireNotCancelled(subscription);
        LocalDate today = time.today();
        if (!request.from().isAfter(today)) {
            throw new BusinessRuleException("A pause can start from tomorrow at the earliest.");
        }
        if (request.to() != null && request.to().isBefore(request.from())) {
            throw new BusinessRuleException("The pause end date can't be before its start date.");
        }
        subscription.setPauseFrom(request.from());
        subscription.setPauseTo(request.to());
        subscription.setStatus(SubscriptionStatus.PAUSED);
        subscriptionRepository.save(subscription);
        cancelPreparedDeliveries(subscription, today, d -> subscription.isPausedOn(d));
        return getMineInTransaction(subscription);
    }

    @Transactional
    public SubscriptionResponse resume(Long id) {
        DairySubscription subscription = ownedForUpdateOrThrow(id);
        requireNotCancelled(subscription);
        subscription.setPauseFrom(null);
        subscription.setPauseTo(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
        return getMineInTransaction(subscription);
    }

    @Transactional
    public SubscriptionResponse skip(Long id, LocalDate date) {
        DairySubscription subscription = ownedForUpdateOrThrow(id);
        requireNotCancelled(subscription);
        LocalDate today = time.today();
        if (!date.isAfter(today)) {
            throw new BusinessRuleException("A delivery can be skipped from tomorrow onwards.");
        }
        if (date.isAfter(today.plusDays(90))) {
            throw new BusinessRuleException("You can skip dates up to 90 days ahead.");
        }
        subscription.getSkipDates().add(date);
        subscriptionRepository.save(subscription);
        cancelPreparedDeliveries(subscription, today, date::equals);
        return getMineInTransaction(subscription);
    }

    /** Takes a skipped date back. A delivery already prepared and cancelled for that date is not re-created. */
    @Transactional
    public SubscriptionResponse unskip(Long id, LocalDate date) {
        DairySubscription subscription = ownedForUpdateOrThrow(id);
        requireNotCancelled(subscription);
        if (!date.isAfter(time.today())) {
            throw new BusinessRuleException("Only upcoming skipped dates can be changed.");
        }
        subscription.getSkipDates().remove(date);
        subscriptionRepository.save(subscription);
        return getMineInTransaction(subscription);
    }

    @Transactional
    public SubscriptionResponse cancel(Long id) {
        DairySubscription subscription = ownedForUpdateOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.CANCELLED) {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscription.setCancelledAt(Instant.now());
            subscriptionRepository.save(subscription);
            cancelPreparedDeliveries(subscription, time.today(), d -> true);
        }
        return getMineInTransaction(subscription);
    }

    /** Cancels waiting deliveries after today whose date matches, giving their stock back. */
    private void cancelPreparedDeliveries(DairySubscription subscription, LocalDate today, java.util.function.Predicate<LocalDate> matches) {
        List<DairyDelivery> waiting = deliveryRepository.findBySubscriptionIdAndStatusAndDeliveryDateGreaterThanEqual(
                subscription.getId(), DairyDeliveryStatus.SCHEDULED, today.plusDays(1));
        for (DairyDelivery delivery : waiting) {
            if (matches.test(delivery.getDeliveryDate())) {
                stockService.restore(delivery);
                delivery.setStatus(DairyDeliveryStatus.SKIPPED_BY_CUSTOMER);
                deliveryRepository.save(delivery);
            }
        }
    }

    private SubscriptionResponse getMineInTransaction(DairySubscription subscription) {
        return toResponse(subscription, productRepository.findById(subscription.getProductId()).orElse(null),
                subscription.getSlotId() == null ? null : slotRepository.findById(subscription.getSlotId()).orElse(null), true);
    }

    private void requireNotCancelled(DairySubscription subscription) {
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new BusinessRuleException("This subscription has been cancelled.");
        }
    }

    private DairySubscription ownedOrThrow(Long id) {
        return subscriptionRepository.findByIdAndUserId(id, currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
    }

    private DairySubscription ownedForUpdateOrThrow(Long id) {
        return subscriptionRepository.findByIdAndUserIdForUpdate(id, currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
    }

    private Map<Long, Product> productsById(List<DairySubscription> subscriptions) {
        Set<Long> ids = subscriptions.stream().map(DairySubscription::getProductId).collect(Collectors.toSet());
        return productRepository.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Map<Long, DeliverySlot> slotsById(List<DairySubscription> subscriptions) {
        Set<Long> ids = subscriptions.stream().map(DairySubscription::getSlotId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return slotRepository.findAllById(ids).stream().collect(Collectors.toMap(DeliverySlot::getId, Function.identity()));
    }

    SubscriptionResponse toResponse(DairySubscription s, Product product, DeliverySlot slot, boolean withUpcoming) {
        LocalDate today = time.today();
        List<LocalDate> futureSkips = s.getSkipDates().stream().filter(d -> d.isAfter(today)).sorted().toList();
        return new SubscriptionResponse(
                s.getId(),
                s.getProductId(),
                product == null ? null : product.getName(),
                product == null ? null : product.getPrice(),
                s.getQuantity(),
                s.getFrequency(),
                s.weekdaySet().stream().sorted().toList(),
                s.getStartDate(),
                s.getSlotId(),
                slot == null ? null : slot.getName(),
                slot == null ? null : slot.getStartTime(),
                slot == null ? null : slot.getEndTime(),
                s.effectiveStatus(today).name(),
                s.getPauseFrom(),
                s.getPauseTo(),
                futureSkips,
                s.getAddressLine1(),
                s.getAddressLine2(),
                s.getAddressCity(),
                s.getAddressState(),
                s.getAddressPincode(),
                withUpcoming ? upcoming(s, today) : List.of(),
                s.getCreatedAt());
    }

    /** The next {@value #UPCOMING_DAYS} days: every day that is, or would have been, a delivery day, and what became of it. */
    List<UpcomingDelivery> upcoming(DairySubscription s, LocalDate today) {
        if (s.getStatus() == SubscriptionStatus.CANCELLED) {
            return List.of();
        }
        LocalDate first = today.plusDays(1);
        List<LocalDate> window = new ArrayList<>();
        for (int i = 0; i < UPCOMING_DAYS; i++) {
            window.add(first.plusDays(i));
        }
        Map<LocalDate, DairyDelivery> prepared = deliveryRepository.findBySubscriptionIdAndDeliveryDateIn(s.getId(), window).stream()
                .collect(Collectors.toMap(DairyDelivery::getDeliveryDate, Function.identity()));
        List<UpcomingDelivery> result = new ArrayList<>();
        for (LocalDate date : window) {
            DairyDelivery delivery = prepared.get(date);
            if (delivery != null) {
                result.add(new UpcomingDelivery(date, delivery.getStatus().name()));
            } else if (s.deliversOn(date)) {
                result.add(new UpcomingDelivery(date, "UPCOMING"));
            } else if (date.isBefore(s.getStartDate())) {
                continue;
            } else if (s.getSkipDates().contains(date)) {
                result.add(new UpcomingDelivery(date, DairyDeliveryStatus.SKIPPED_BY_CUSTOMER.name()));
            }
        }
        return result;
    }
}
