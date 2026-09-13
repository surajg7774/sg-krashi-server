package com.sgkrashi.payout.event;

import com.sgkrashi.notification.event.RefundProcessedEvent;
import com.sgkrashi.payout.service.PayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Separate from {@code NotificationEventListener} — this reacts to a refund
 * with a ledger side effect (a payout clawback), not a customer
 * notification, so it deliberately lives in its own listener rather than
 * being folded into that one.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}, same
 * reasoning as {@code NotificationEventListener}'s Javadoc: {@code
 * RefundProcessedEvent} is published from inside {@code
 * OrderServiceImpl#markRefunded}'s transaction, so this must only run once
 * that transaction has actually committed — never on a refund that ends up
 * rolling back.
 */
@Component
public class PayoutRefundEventListener {

    private static final Logger log = LoggerFactory.getLogger(PayoutRefundEventListener.class);
    private static final String ORDER_PAYABLE_TYPE = "ORDER";

    private final PayoutService payoutService;

    public PayoutRefundEventListener(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundProcessed(RefundProcessedEvent event) {
        log.info("onRefundProcessed: received event payableType={} payableId={}", event.payableType(), event.payableId());
        // Bookings are out of scope for this payout system (see
        // FarmerPayout's migration note) — nothing to claw back for one.
        if (!ORDER_PAYABLE_TYPE.equals(event.payableType())) {
            return;
        }
        payoutService.handleOrderRefunded(event.payableId());
    }
}
