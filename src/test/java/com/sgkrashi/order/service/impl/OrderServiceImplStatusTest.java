package com.sgkrashi.order.service.impl;

import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.notification.event.OrderConfirmedEvent;
import com.sgkrashi.notification.event.OrderDeliveredEvent;
import com.sgkrashi.notification.event.OrderShippedEvent;
import com.sgkrashi.notification.event.PaymentFailedEvent;
import com.sgkrashi.notification.event.RefundProcessedEvent;
import com.sgkrashi.order.dto.response.AdminOrderDetailResponse;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderItem;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.entity.OrderStatusHistory;
import com.sgkrashi.order.entity.StatusChangeActor;
import com.sgkrashi.order.mapper.OrderMapper;
import com.sgkrashi.order.repository.OrderItemRepository;
import com.sgkrashi.order.repository.OrderRepository;
import com.sgkrashi.order.repository.OrderStatusHistoryRepository;
import com.sgkrashi.payment.entity.Payment;
import com.sgkrashi.payment.entity.PaymentStatus;
import com.sgkrashi.payment.repository.PaymentRepository;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No database: repositories are mocks, so these pin the rules inside
 * {@code applyTransition} — which moves are allowed, who is recorded, and that
 * a repeat or a late webhook neither writes a second history row nor sends a
 * second notification.
 */
class OrderServiceImplStatusTest {

    private static final long ORDER_ID = 7L;
    private static final long ADMIN_ID = 99L;

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditLogService audit = mock(AuditLogService.class);

    private final List<OrderStatusHistory> savedHistory = new ArrayList<>();
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(
                orderRepository, orderItemRepository, historyRepository,
                null, null, productRepository, null, null, mock(MediaAssetRepository.class),
                userRepository, paymentRepository, currentUser, new OrderMapper(), events, audit);
        when(historyRepository.save(any())).thenAnswer(inv -> {
            OrderStatusHistory h = inv.getArgument(0);
            savedHistory.add(h);
            return h;
        });
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(anyLong())).thenReturn(savedHistory);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Default: an unauthenticated caller, i.e. the payment webhook.
        when(currentUser.getCurrentUserIdOrNull()).thenReturn(null);
    }

    private Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setUserId(5L);
        o.setOrderNumber("SGK-7");
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal("250.00"));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(o));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));
        User customer = new User();
        customer.setId(5L);
        customer.setName("Asha");
        customer.setEmail("asha@example.com");
        when(userRepository.findById(5L)).thenReturn(Optional.of(customer));
        return o;
    }

    private void asAdmin() {
        when(currentUser.getCurrentUserIdOrNull()).thenReturn(ADMIN_ID);
        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setName("Priya Admin");
        when(userRepository.findAllById(any())).thenReturn(List.of(admin));
    }

    private Object publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }

    // ---- who is recorded --------------------------------------------------------------------

    @Test
    void theWebhookIsRecordedAsSystemWithNoUserId() {
        Order o = order(OrderStatus.PENDING_PAYMENT);

        service.markConfirmed(ORDER_ID);

        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
        assertEquals(1, savedHistory.size());
        assertEquals(StatusChangeActor.Role.SYSTEM, savedHistory.get(0).getChangedByRole());
        assertNull(savedHistory.get(0).getChangedByUserId());
        assertEquals("Payment confirmed", savedHistory.get(0).getNote());
        assertInstanceOf(OrderConfirmedEvent.class, publishedEvent());
    }

    @Test
    void anAdminChangeRecordsTheAdminsRoleAndId() {
        order(OrderStatus.CONFIRMED);
        asAdmin();

        service.updateOrderStatus(ORDER_ID, OrderStatus.SHIPPED, "left the depot");

        OrderStatusHistory row = savedHistory.get(0);
        assertEquals(OrderStatus.SHIPPED, row.getStatus());
        assertEquals(StatusChangeActor.Role.ADMIN, row.getChangedByRole());
        assertEquals(ADMIN_ID, row.getChangedByUserId());
    }

    @Test
    void theAdminViewShowsWhoDidItButTheCustomerNeverGetsTheActor() {
        order(OrderStatus.CONFIRMED);
        asAdmin();

        AdminOrderDetailResponse detail = service.updateOrderStatus(ORDER_ID, OrderStatus.SHIPPED, null);

        assertEquals("Priya Admin", detail.statusHistory().get(0).changedByName());
        assertEquals(StatusChangeActor.Role.ADMIN, detail.statusHistory().get(0).changedByRole());
    }

    // ---- the approved moves -----------------------------------------------------------------

    @Test
    void shippedThenDeliveredWorksAndEachSendsExactlyOneEvent() {
        Order o = order(OrderStatus.CONFIRMED);
        asAdmin();

        service.updateOrderStatus(ORDER_ID, OrderStatus.SHIPPED, null);
        service.updateOrderStatus(ORDER_ID, OrderStatus.DELIVERED, null);

        assertEquals(OrderStatus.DELIVERED, o.getStatus());
        assertEquals(List.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED),
                savedHistory.stream().map(OrderStatusHistory::getStatus).toList());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertInstanceOf(OrderShippedEvent.class, captor.getAllValues().get(0));
        assertInstanceOf(OrderDeliveredEvent.class, captor.getAllValues().get(1));
    }

    @Test
    void anOrderMaySkipShipped() {
        Order o = order(OrderStatus.CONFIRMED);
        asAdmin();

        service.updateOrderStatus(ORDER_ID, OrderStatus.DELIVERED, null);

        assertEquals(OrderStatus.DELIVERED, o.getStatus());
        assertEquals(1, savedHistory.size());
    }

    @Test
    void settingTheStatusTheOrderAlreadyHasSendsNothingAndWritesNoHistory() {
        Order o = order(OrderStatus.SHIPPED);
        asAdmin();

        AdminOrderDetailResponse detail = service.updateOrderStatus(ORDER_ID, OrderStatus.SHIPPED, "just a note");

        assertEquals(OrderStatus.SHIPPED, o.getStatus());
        assertEquals("just a note", o.getAdminNotes());
        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
        assertNotNull(detail);
    }

    @Test
    void notesCanBeSavedOnAnyOrderWithoutChangingItsStatus() {
        Order o = order(OrderStatus.PENDING_PAYMENT);
        asAdmin();

        service.updateOrderStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT, "customer called");

        assertEquals("customer called", o.getAdminNotes());
        assertEquals(OrderStatus.PENDING_PAYMENT, o.getStatus());
        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aSecondMarkShippedCallIsANoOp() {
        order(OrderStatus.SHIPPED);

        service.markShipped(ORDER_ID);

        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
    }

    // ---- rejected moves ---------------------------------------------------------------------

    @Test
    void goingBackwardsIsRejectedWithAClearMessageAndChangesNothing() {
        Order o = order(OrderStatus.DELIVERED);
        asAdmin();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED, "oops"));

        assertEquals("This order is Delivered and cannot be changed to Confirmed", ex.getMessage());
        assertEquals(OrderStatus.DELIVERED, o.getStatus());
        assertNull(o.getAdminNotes(), "a rejected request must not save its notes either");
        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shippedIsRejectedUnlessThePaymentWasConfirmed() {
        order(OrderStatus.PENDING_PAYMENT);
        asAdmin();

        assertThrows(BusinessRuleException.class, () -> service.updateOrderStatus(ORDER_ID, OrderStatus.SHIPPED, null));
        assertThrows(BusinessRuleException.class, () -> service.updateOrderStatus(ORDER_ID, OrderStatus.DELIVERED, null));
        assertTrue(savedHistory.isEmpty());
    }

    @Test
    void aConfirmedOrderCannotBeMarkedPaymentFailedAnymore() {
        Order o = order(OrderStatus.CONFIRMED);
        asAdmin();

        assertThrows(BusinessRuleException.class, () -> service.updateOrderStatus(ORDER_ID, OrderStatus.PAYMENT_FAILED, null));
        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
    }

    @Test
    void anAdminCanNeverSetRefundedOrPendingThroughTheStatusEndpoint() {
        order(OrderStatus.CONFIRMED);
        asAdmin();

        assertThrows(BusinessRuleException.class, () -> service.updateOrderStatus(ORDER_ID, OrderStatus.REFUNDED, null));
        assertThrows(BusinessRuleException.class, () -> service.updateOrderStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT, null));
    }

    @Test
    void aFailedOrderCannotBeConfirmedByAnAdmin() {
        Order o = order(OrderStatus.PAYMENT_FAILED);
        asAdmin();

        assertThrows(BusinessRuleException.class, () -> service.updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED, null));
        assertEquals(OrderStatus.PAYMENT_FAILED, o.getStatus());
    }

    // ---- webhook edge cases -----------------------------------------------------------------

    @Test
    void aLateCapturedPaymentOnAFailedOrderLeavesItFailedAndSendsNothing() {
        Order o = order(OrderStatus.PAYMENT_FAILED);

        service.markConfirmed(ORDER_ID);

        assertEquals(OrderStatus.PAYMENT_FAILED, o.getStatus());
        assertTrue(savedHistory.isEmpty());
        verify(orderRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aDuplicateWebhookOnAnOrderThatMovedOnIsIgnored() {
        for (OrderStatus later : List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED)) {
            Order o = order(later);
            service.markConfirmed(ORDER_ID);
            assertEquals(later, o.getStatus());
        }
        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aFailureEventForAnOrderThatWasConfirmedMeanwhileIsIgnored() {
        Order o = order(OrderStatus.CONFIRMED);

        service.markPaymentFailed(ORDER_ID);

        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aFailedPaymentRestoresStockAndNotifiesOnce() {
        Order o = order(OrderStatus.PENDING_PAYMENT);
        Product product = new Product();
        product.setId(11L);
        product.setStockQty(3);
        OrderItem item = new OrderItem();
        item.setItemType(ItemType.PRODUCT);
        item.setProduct(product);
        item.setQuantity(2);
        when(orderItemRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(item));
        when(productRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(product));

        service.markPaymentFailed(ORDER_ID);

        assertEquals(OrderStatus.PAYMENT_FAILED, o.getStatus());
        assertEquals(5, product.getStockQty());
        assertEquals(StatusChangeActor.Role.SYSTEM, savedHistory.get(0).getChangedByRole());
        assertInstanceOf(PaymentFailedEvent.class, publishedEvent());
    }

    // ---- refunds ----------------------------------------------------------------------------

    @Test
    void refundIsAllowedFromConfirmedShippedDeliveredAndFailed() {
        for (OrderStatus from : List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.PAYMENT_FAILED)) {
            Order o = order(from);
            service.assertCanBeRefunded(ORDER_ID);
            service.markRefunded(ORDER_ID);
            assertEquals(OrderStatus.REFUNDED, o.getStatus(), "from " + from);
        }
        assertEquals(4, savedHistory.size());
    }

    @Test
    void refundIsCheckedBeforeTheGatewayIsEverCalled() {
        order(OrderStatus.PENDING_PAYMENT);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assertCanBeRefunded(ORDER_ID));

        assertEquals("An order that is Pending Payment cannot be refunded", ex.getMessage());
    }

    @Test
    void anAlreadyRefundedOrderStaysIdempotent() {
        order(OrderStatus.REFUNDED);

        service.assertCanBeRefunded(ORDER_ID);
        service.markRefunded(ORDER_ID);

        assertTrue(savedHistory.isEmpty());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void theRefundEventCarriesTheOrdersTotalForThePayoutClawback() {
        order(OrderStatus.DELIVERED);

        service.markRefunded(ORDER_ID);

        RefundProcessedEvent event = assertInstanceOf(RefundProcessedEvent.class, publishedEvent());
        assertEquals("ORDER", event.payableType());
        assertEquals(new BigDecimal("250.00"), event.amount());
    }

    // ---- surfacing the late-capture case to admins -----------------------------------------

    @Test
    void anAdminSeesAnAttentionMessageWhenAPaidOrderIsStuckAsPaymentFailed() {
        order(OrderStatus.PAYMENT_FAILED);
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("250.00"));
        when(paymentRepository.findByPayableTypeAndPayableId("ORDER", ORDER_ID)).thenReturn(Optional.of(payment));

        AdminOrderDetailResponse detail = service.getOrderDetailForAdmin(ORDER_ID);

        assertEquals("PAID", detail.paymentStatus());
        assertNotNull(detail.attentionMessage());
        assertTrue(detail.attentionMessage().contains("250.00"));
    }

    @Test
    void aNormalOrderHasNoAttentionMessage() {
        order(OrderStatus.CONFIRMED);
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("250.00"));
        when(paymentRepository.findByPayableTypeAndPayableId("ORDER", ORDER_ID)).thenReturn(Optional.of(payment));

        assertNull(service.getOrderDetailForAdmin(ORDER_ID).attentionMessage());
    }
}
