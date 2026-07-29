package com.sgkrashi.cart.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.cart.dto.request.AddCartItemRequest;
import com.sgkrashi.cart.dto.request.UpdateCartItemRequest;
import com.sgkrashi.cart.dto.response.CartResponse;
import com.sgkrashi.cart.entity.Cart;
import com.sgkrashi.cart.entity.CartItem;
import com.sgkrashi.cart.mapper.CartMapper;
import com.sgkrashi.cart.repository.CartItemRepository;
import com.sgkrashi.cart.repository.CartRepository;
import com.sgkrashi.cart.service.CartService;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private static final String PRODUCT_OWNER_TYPE = "PRODUCT";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CartMapper cartMapper;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            MediaAssetRepository mediaAssetRepository,
            CurrentUserProvider currentUserProvider,
            CartMapper cartMapper
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.currentUserProvider = currentUserProvider;
        this.cartMapper = cartMapper;
    }

    @Override
    public CartResponse getCart() {
        Long userId = currentUserProvider.getCurrentUserId();
        return cartRepository.findByUserId(userId)
                .map(cart -> buildResponse(cartItemRepository.findByCartId(cart.getId())))
                .orElseGet(CartResponse::empty);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Product product = productRepository.findByIdAndIsActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int existingQuantity = item != null ? item.getQuantity() : 0;
        int newQuantity = existingQuantity + request.quantity();
        requireStockAvailable(product, newQuantity);

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
        }
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return buildResponse(cartItemRepository.findByCartId(cart.getId()));
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        CartItem item = getOwnedCartItemOrThrow(itemId);
        requireStockAvailable(item.getProduct(), request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        return buildResponse(cartItemRepository.findByCartId(item.getCart().getId()));
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long itemId) {
        CartItem item = getOwnedCartItemOrThrow(itemId);
        Long cartId = item.getCart().getId();
        cartItemRepository.delete(item);

        return buildResponse(cartItemRepository.findByCartId(cartId));
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUserId(userId);
            return cartRepository.save(cart);
        });
    }

    /**
     * UX-nicety check only — the authoritative check happens again, under a
     * pessimistic lock, at checkout time (OrderServiceImpl), since stock can
     * change between now and then.
     */
    private void requireStockAvailable(Product product, int requestedQuantity) {
        if (product.getStockQty() < requestedQuantity) {
            throw new BusinessRuleException(
                    "Only " + product.getStockQty() + " unit(s) of \"" + product.getName() + "\" available");
        }
    }

    /**
     * Verify the cart item belongs to the authenticated user's own cart —
     * never trust the path param alone. A mismatch surfaces as 404, matching
     * Module 4's address-ownership pattern.
     */
    private CartItem getOwnedCartItemOrThrow(Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        Long userId = currentUserProvider.getCurrentUserId();
        if (!item.getCart().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found");
        }
        return item;
    }

    private CartResponse buildResponse(List<CartItem> items) {
        List<Long> productIds = items.stream().map(item -> item.getProduct().getId()).toList();
        Map<Long, String> thumbnailsByProductId = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(PRODUCT_OWNER_TYPE, productIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));

        return cartMapper.toCartResponse(items, thumbnailsByProductId);
    }
}
