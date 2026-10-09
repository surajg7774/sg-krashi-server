package com.sgkrashi.dairy.service;

import com.sgkrashi.dairy.entity.DairyDelivery;
import com.sgkrashi.dairy.entity.DairyDeliveryStatus;
import com.sgkrashi.dairy.entity.DairySubscription;
import com.sgkrashi.dairy.entity.DeliverySlot;
import com.sgkrashi.dairy.entity.SubscriptionStatus;
import com.sgkrashi.dairy.repository.DairyDeliveryRepository;
import com.sgkrashi.dairy.repository.DairySubscriptionRepository;
import com.sgkrashi.dairy.repository.DeliverySlotRepository;
import com.sgkrashi.dairy.service.DairySubscriptionEvent.Kind;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepares the deliveries for one date, one subscription per transaction.
 *
 * <p>Safe to run any number of times for the same date: a subscription that already has a row for that date is left
 * alone, and the database's unique key on (subscription, date) is the backstop if two runs ever overlap (the loser's
 * transaction, including its stock change, is rolled back). Stock is taken under the product's row lock, the same lock
 * checkout uses, so a unit can be sold or reserved only once. The subscription row is locked first, then the product,
 * the same order the customer actions use, so the two cannot deadlock each other.
 *
 * <p>When stock is short the delivery is recorded as SKIPPED_OUT_OF_STOCK and the customer is told; nothing is
 * oversold. Subscriptions are served in id order, so the earliest subscribers get the last units.
 */
@Service
public class DairyDeliveryPreparationService {

    private static final Logger log = LoggerFactory.getLogger(DairyDeliveryPreparationService.class);

    public enum Outcome {
        SCHEDULED, SKIPPED_OUT_OF_STOCK, NOT_DUE, ALREADY_PREPARED
    }

    /** Counts for the log line of one run. */
    public record RunSummary(LocalDate date, int considered, int scheduled, int outOfStock, int alreadyPrepared, int failed) {
    }

    private final DairySubscriptionRepository subscriptionRepository;
    private final DairyDeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;
    private final DeliverySlotRepository slotRepository;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public DairyDeliveryPreparationService(
            DairySubscriptionRepository subscriptionRepository,
            DairyDeliveryRepository deliveryRepository,
            ProductRepository productRepository,
            DeliverySlotRepository slotRepository,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.productRepository = productRepository;
        this.slotRepository = slotRepository;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public RunSummary prepareFor(LocalDate date) {
        List<Long> ids = subscriptionRepository.findIdsToConsider(date, SubscriptionStatus.CANCELLED);
        int scheduled = 0;
        int outOfStock = 0;
        int already = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Long id : ids) {
            try {
                Outcome outcome = transaction.execute(status -> prepareOne(id, date));
                if (outcome == Outcome.SCHEDULED) {
                    scheduled++;
                } else if (outcome == Outcome.SKIPPED_OUT_OF_STOCK) {
                    outOfStock++;
                } else if (outcome == Outcome.ALREADY_PREPARED) {
                    already++;
                }
            } catch (RuntimeException ex) {
                failedIds.add(id);
                log.warn("Dairy delivery preparation failed for subscription {} on {}: {}", id, date, ex.getClass().getSimpleName());
            }
        }

        // One more pass over any that failed (a deadlock or a dropped connection is usually gone a moment later).
        int failed = 0;
        for (Long id : failedIds) {
            try {
                Outcome outcome = transaction.execute(status -> prepareOne(id, date));
                if (outcome == Outcome.SCHEDULED) {
                    scheduled++;
                } else if (outcome == Outcome.SKIPPED_OUT_OF_STOCK) {
                    outOfStock++;
                } else if (outcome == Outcome.ALREADY_PREPARED) {
                    already++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Dairy delivery preparation failed again for subscription {} on {}: {}", id, date, ex.getClass().getSimpleName());
            }
        }
        return new RunSummary(date, ids.size(), scheduled, outOfStock, already, failed);
    }

    /** Must run inside a transaction. */
    Outcome prepareOne(Long subscriptionId, LocalDate date) {
        DairySubscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (subscription == null || !subscription.deliversOn(date)) {
            return Outcome.NOT_DUE;
        }
        if (deliveryRepository.existsBySubscriptionIdAndDeliveryDate(subscriptionId, date)) {
            return Outcome.ALREADY_PREPARED;
        }

        Product product = productRepository.findByIdForUpdate(subscription.getProductId()).orElse(null);
        DeliverySlot slot = subscription.getSlotId() == null ? null : slotRepository.findById(subscription.getSlotId()).orElse(null);

        DairyDelivery delivery = new DairyDelivery();
        delivery.setSubscriptionId(subscriptionId);
        delivery.setDeliveryDate(date);
        delivery.setQuantity(subscription.getQuantity());
        delivery.setProductId(subscription.getProductId());
        delivery.setProductName(product == null ? "Dairy product" : product.getName());
        java.math.BigDecimal price = product == null ? java.math.BigDecimal.ZERO : product.getPrice();
        delivery.setUnitPrice(price);
        delivery.setLineTotal(price.multiply(java.math.BigDecimal.valueOf(subscription.getQuantity())));
        if (slot != null) {
            delivery.setSlotName(slot.getName());
            delivery.setSlotStart(slot.getStartTime());
            delivery.setSlotEnd(slot.getEndTime());
        }
        delivery.setAddressLine1(subscription.getAddressLine1());
        delivery.setAddressLine2(subscription.getAddressLine2());
        delivery.setAddressCity(subscription.getAddressCity());
        delivery.setAddressState(subscription.getAddressState());
        delivery.setAddressPincode(subscription.getAddressPincode());

        boolean enough = product != null && product.isActive() && product.getStockQty() >= subscription.getQuantity();
        delivery.setStatus(enough ? DairyDeliveryStatus.SCHEDULED : DairyDeliveryStatus.SKIPPED_OUT_OF_STOCK);
        delivery.setStockReserved(enough);

        // Written first and flushed: if another run got here first, the unique key fails right now and the whole
        // transaction rolls back before any stock has been touched.
        deliveryRepository.saveAndFlush(delivery);

        if (enough) {
            product.setStockQty(product.getStockQty() - subscription.getQuantity());
            productRepository.save(product);
            return Outcome.SCHEDULED;
        }
        events.publishEvent(new DairySubscriptionEvent(Kind.DELIVERY_SKIPPED_OUT_OF_STOCK, subscription.getUserId(),
                subscriptionId, delivery.getProductName(), date, null));
        return Outcome.SKIPPED_OUT_OF_STOCK;
    }
}
