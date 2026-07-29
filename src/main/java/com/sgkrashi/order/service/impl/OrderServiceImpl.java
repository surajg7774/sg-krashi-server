package com.sgkrashi.order.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.cart.entity.Cart;
import com.sgkrashi.cart.entity.CartItem;
import com.sgkrashi.cart.repository.CartItemRepository;
import com.sgkrashi.cart.repository.CartRepository;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
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
    private static final String CROP_LISTING_OWNER_TYPE = "CROP_LISTING";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CropListingRepository cropListingRepository;
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
            CropListingRepository cropListingRepository,
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
        this.cropListingRepository = cropListingRepository;
        this.addressRepository = addressRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.currentUserProvider = currentUserProvider;
        this.orderMapper = orderMapper;
    }

    /** One locked row (product or crop listing) paired with the cart line that references it. */
    private record LockedLine(CartItem cartItem, Product product, CropListing cropListing) {

        String itemName() {
            return product != null ? product.getName() : cropListing.getName();
        }

        BigDecimal unitPrice() {
            return product != null ? product.getPrice() : cropListing.getUnitPrice();
        }

        boolean isActive() {
            return product != null ? product.isActive() : cropListing.isActive();
        }

        int availableQuantity() {
            return product != null ? product.getStockQty() : cropListing.getQuantityAvailable();
        }
    }

    /**
     * The critical transaction of this module. Correctness invariants:
     * <ol>
     *   <li>Cart lines are locked in a fixed, global order — {@code (itemType, itemId)}
     *       ascending — across BOTH Products and CropListings, so two concurrent
     *       checkouts that share any row (of either type) can never deadlock
     *       waiting on each other's locks.</li>
     *   <li>Every line is validated (still active, still enough stock/quantity)
     *       BEFORE any row is mutated, so a failure partway through never leaves
     *       a half-decremented order.</li>
     *   <li>Price and name are copied onto the order item at this instant and
     *       never recomputed from the live product/listing afterward.</li>
     *   <li>Stock/quantity is decremented under the same lock that validated it,
     *       so the check-then-act is atomic with respect to any other
     *       transaction — MySQL's SELECT ... FOR UPDATE always reads the latest
     *       committed row, so a second transaction blocked on the lock
     *       re-validates against the first transaction's already-applied
     *       decrement once it proceeds.</li>
     * </ol>
     * Module 6 found (and fixed) a bug where eagerly-loaded Product entities
     * attached to the persistence context BEFORE the locked fetch caused the
     * locked query to return stale data despite the DB lock being real. The
     * same risk applies here for CropListing, which is why cart items are
     * fetched via {@code findAllByCartId} (no eager association) exactly as
     * Module 6 established.
     */
    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Address address = getOwnedAddressOrThrow(request.addressId(), userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cart is empty");
        }

        List<CartItem> sortedItems = cartItems.stream()
                .sorted(Comparator
                        .comparing((CartItem item) -> item.getItemType().name())
                        .thenComparing(CartItem::getReferencedItemId))
                .toList();

        List<LockedLine> lockedLines = new ArrayList<>();
        for (CartItem item : sortedItems) {
            if (item.getItemType() == ItemType.PRODUCT) {
                Product locked = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                lockedLines.add(new LockedLine(item, locked, null));
            } else {
                CropListing locked = cropListingRepository.findByIdForUpdate(item.getCropListing().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
                lockedLines.add(new LockedLine(item, null, locked));
            }
        }

        for (LockedLine line : lockedLines) {
            if (!line.isActive()) {
                throw new BusinessRuleException("\"" + line.itemName() + "\" is no longer available");
            }
            if (line.availableQuantity() < line.cartItem().getQuantity()) {
                throw new BusinessRuleException(
                        "Only " + line.availableQuantity() + " unit(s) of \"" + line.itemName() + "\" available");
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
        for (LockedLine line : lockedLines) {
            int quantity = line.cartItem().getQuantity();
            BigDecimal lineTotal = line.unitPrice().multiply(BigDecimal.valueOf(quantity));
            total = total.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            if (line.product() != null) {
                orderItem.setItemType(ItemType.PRODUCT);
                orderItem.setProduct(line.product());
                line.product().setStockQty(line.product().getStockQty() - quantity);
                productRepository.save(line.product());
            } else {
                orderItem.setItemType(ItemType.CROP_LISTING);
                orderItem.setCropListing(line.cropListing());
                line.cropListing().setQuantityAvailable(line.cropListing().getQuantityAvailable() - quantity);
                cropListingRepository.save(line.cropListing());
            }
            orderItem.setItemNameSnapshot(line.itemName());
            orderItem.setUnitPriceSnapshot(line.unitPrice());
            orderItem.setQuantity(quantity);
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);
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
     * checkout decrements stock/quantity at order-creation time, before payment
     * settles, so a failed payment must give it back or it leaks permanently.
     * Distinct from cancellation/refunds, which remain out of scope.
     */
    @Override
    @Transactional
    public void markPaymentFailed(Long orderId) {
        Order order = getOrderEntityOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        items.stream()
                .sorted(Comparator
                        .comparing((OrderItem item) -> item.getItemType().name())
                        .thenComparing(OrderItem::getReferencedItemId))
                .forEach(item -> {
                    if (item.getItemType() == ItemType.PRODUCT) {
                        Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                        product.setStockQty(product.getStockQty() + item.getQuantity());
                        productRepository.save(product);
                    } else {
                        CropListing cropListing = cropListingRepository.findByIdForUpdate(item.getCropListing().getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
                        cropListing.setQuantityAvailable(cropListing.getQuantityAvailable() + item.getQuantity());
                        cropListingRepository.save(cropListing);
                    }
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

        List<Long> productIds = items.stream()
                .filter(item -> item.getItemType() == ItemType.PRODUCT)
                .map(item -> item.getProduct().getId())
                .toList();
        List<Long> cropListingIds = items.stream()
                .filter(item -> item.getItemType() == ItemType.CROP_LISTING)
                .map(item -> item.getCropListing().getId())
                .toList();

        Map<Long, String> productThumbnails = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(PRODUCT_OWNER_TYPE, productIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));
        Map<Long, String> cropListingThumbnails = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(CROP_LISTING_OWNER_TYPE, cropListingIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));

        return orderMapper.toOrderResponse(order, items, history, productThumbnails, cropListingThumbnails);
    }
}
