package com.sgkrashi.order.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.cart.entity.Cart;
import com.sgkrashi.cart.entity.CartItem;
import com.sgkrashi.cart.repository.CartItemRepository;
import com.sgkrashi.cart.repository.CartRepository;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.customer.entity.Address;
import com.sgkrashi.customer.repository.AddressRepository;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.order.dto.request.CheckoutRequest;
import com.sgkrashi.order.dto.response.OrderResponse;
import com.sgkrashi.order.dto.response.OrderSummaryResponse;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderItem;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.entity.OrderStatusHistory;
import com.sgkrashi.order.mapper.OrderMapper;
import com.sgkrashi.order.repository.OrderItemRepository;
import com.sgkrashi.order.repository.OrderRepository;
import com.sgkrashi.order.repository.OrderStatusHistoryRepository;
import com.sgkrashi.order.service.OrderService;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String PRODUCT_OWNER_TYPE = "PRODUCT";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            AddressRepository addressRepository,
            MediaAssetRepository mediaAssetRepository,
            CurrentUserProvider currentUserProvider,
            OrderMapper orderMapper
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.currentUserProvider = currentUserProvider;
        this.orderMapper = orderMapper;
    }

    /**
     * The critical transaction of this module. Correctness invariants:
     * <ol>
     *   <li>Cart lines are locked in ascending product-ID order — a fixed, global
     *       ordering — so two concurrent checkouts that share a product can never
     *       deadlock waiting on each other's locks.</li>
     *   <li>Every line is validated (product still active, still enough stock)
     *       BEFORE any row is mutated, so a failure partway through never leaves
     *       a half-decremented order.</li>
     *   <li>Price and product name are copied onto the order item at this instant
     *       and never recomputed from the live product afterward.</li>
     *   <li>Stock is decremented under the same lock that validated it, so the
     *       check-then-act is atomic with respect to any other transaction — MySQL's
     *       SELECT ... FOR UPDATE always reads the latest committed row, so a second
     *       transaction blocked on the lock re-validates against the first
     *       transaction's already-applied decrement once it proceeds.</li>
     * </ol>
     */
    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Address address = getOwnedAddressOrThrow(request.addressId(), userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Cart is empty"));
        // findAllByCartId (not findByCartId): the latter eagerly loads Product via
        // @EntityGraph, which would attach a pre-lock (potentially stale) Product
        // instance to this persistence context before the locked fetch below runs.
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cart is empty");
        }

        List<CartItem> sortedItems = cartItems.stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        List<Product> lockedProducts = new ArrayList<>();
        for (CartItem item : sortedItems) {
            Product locked = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            lockedProducts.add(locked);
        }

        for (int i = 0; i < sortedItems.size(); i++) {
            CartItem item = sortedItems.get(i);
            Product product = lockedProducts.get(i);
            if (!product.isActive()) {
                throw new BusinessRuleException("\"" + product.getName() + "\" is no longer available");
            }
            if (product.getStockQty() < item.getQuantity()) {
                throw new BusinessRuleException(
                        "Only " + product.getStockQty() + " unit(s) of \"" + product.getName() + "\" available");
            }
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setShippingLine1(address.getLine1());
        order.setShippingLine2(address.getLine2());
        order.setShippingCity(address.getCity());
        order.setShippingState(address.getState());
        order.setShippingPincode(address.getPincode());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < sortedItems.size(); i++) {
            CartItem cartItem = sortedItems.get(i);
            Product product = lockedProducts.get(i);

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductNameSnapshot(product.getName());
            orderItem.setUnitPriceSnapshot(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);

            product.setStockQty(product.getStockQty() - cartItem.getQuantity());
            productRepository.save(product);
        }
        order.setTotalAmount(total);

        Order savedOrder = orderRepository.save(order);
        orderItems.forEach(item -> item.setOrder(savedOrder));
        orderItemRepository.saveAll(orderItems);

        recordStatusHistory(savedOrder, OrderStatus.PENDING_PAYMENT, "Order placed");
        cartItemRepository.deleteByCartId(cart.getId());

        return buildOrderResponse(savedOrder);
    }

    @Override
    public PaginatedResponse<OrderSummaryResponse> listMyOrders(int page, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        Page<Order> ordersPage = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<OrderSummaryResponse> summaries = ordersPage.getContent().stream()
                .map(order -> orderMapper.toSummaryResponse(order, (int) orderItemRepository.countByOrderId(order.getId())))
                .toList();
        return PaginatedResponse.of(summaries, ordersPage);
    }

    @Override
    public OrderResponse getOrderDetail(Long orderId) {
        return buildOrderResponse(getOwnedOrderOrThrow(orderId));
    }

    @Override
    @Transactional
    public void markConfirmed(Long orderId) {
        Order order = getOrderEntityOrThrow(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        recordStatusHistory(order, OrderStatus.CONFIRMED, "Payment confirmed");
    }

    /**
     * Beyond the module's literal checklist, but necessary for correctness:
     * checkout decrements stock at order-creation time, before payment settles,
     * so a failed payment must give that stock back or it leaks permanently.
     * Distinct from cancellation/refunds, which remain out of scope.
     */
    @Override
    @Transactional
    public void markPaymentFailed(Long orderId) {
        Order order = getOrderEntityOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        // findAllByOrderId (not findByOrderId): avoid eagerly attaching Product before
        // the locked fetch below, for the same reason as checkout() — see
        // CartItemRepository.findAllByCartId's Javadoc.
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        items.stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> {
                    Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                    product.setStockQty(product.getStockQty() + item.getQuantity());
                    productRepository.save(product);
                });

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
        recordStatusHistory(order, OrderStatus.PAYMENT_FAILED, "Payment failed — stock restored");
    }

    @Override
    public Order getOrderEntityOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private String generateOrderNumber() {
        String timePart = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String randomPart = Integer.toString(ThreadLocalRandom.current().nextInt(46656), 36).toUpperCase();
        return "ORD-" + timePart + "-" + randomPart;
    }

    private void recordStatusHistory(Order order, OrderStatus status, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(status);
        history.setNote(note);
        orderStatusHistoryRepository.save(history);
    }

    private Address getOwnedAddressOrThrow(Long addressId, Long userId) {
        Address address = addressRepository.findById(addressId)
                .filter(Address::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address not found");
        }
        return address;
    }

    private Order getOwnedOrderOrThrow(Long orderId) {
        Order order = getOrderEntityOrThrow(orderId);
        Long userId = currentUserProvider.getCurrentUserId();
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        return order;
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        List<Long> productIds = items.stream().map(item -> item.getProduct().getId()).toList();
        Map<Long, String> thumbnailsByProductId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(PRODUCT_OWNER_TYPE, productIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));

        return orderMapper.toOrderResponse(order, items, history, thumbnailsByProductId);
    }
}
