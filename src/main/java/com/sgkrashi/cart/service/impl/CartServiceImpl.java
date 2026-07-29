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
import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
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
    private static final String CROP_LISTING_OWNER_TYPE = "CROP_LISTING";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CropListingRepository cropListingRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CartMapper cartMapper;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            CropListingRepository cropListingRepository,
            MediaAssetRepository mediaAssetRepository,
            CurrentUserProvider currentUserProvider,
            CartMapper cartMapper
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.cropListingRepository = cropListingRepository;
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
        Cart cart = getOrCreateCart(userId);

        if (request.itemType() == ItemType.PRODUCT) {
            addProductItem(cart, request.itemId(), request.quantity());
        } else {
            addCropListingItem(cart, request.itemId(), request.quantity());
        }

        return buildResponse(cartItemRepository.findByCartId(cart.getId()));
    }

    private void addProductItem(Cart cart, Long productId, int quantity) {
        Product product = productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
        int newQuantity = (item != null ? item.getQuantity() : 0) + quantity;
        requireAvailable(newQuantity, product.getStockQty(), product.getName());

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setItemType(ItemType.PRODUCT);
            item.setProduct(product);
        }
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
    }

    private void addCropListingItem(Cart cart, Long cropListingId, int quantity) {
        CropListing cropListing = cropListingRepository.findByIdAndIsActiveTrue(cropListingId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));

        CartItem item = cartItemRepository.findByCartIdAndCropListingId(cart.getId(), cropListing.getId()).orElse(null);
        int newQuantity = (item != null ? item.getQuantity() : 0) + quantity;
        requireAvailable(newQuantity, cropListing.getQuantityAvailable(), cropListing.getName());

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setItemType(ItemType.CROP_LISTING);
            item.setCropListing(cropListing);
        }
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        CartItem item = getOwnedCartItemOrThrow(itemId);
        if (item.getItemType() == ItemType.PRODUCT) {
            Product product = item.getProduct();
            requireAvailable(request.quantity(), product.getStockQty(), product.getName());
        } else {
            CropListing cropListing = item.getCropListing();
            requireAvailable(request.quantity(), cropListing.getQuantityAvailable(), cropListing.getName());
        }
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
     * pessimistic lock, at checkout time (OrderServiceImpl), since availability
     * can change between now and then.
     */
    private void requireAvailable(int requestedQuantity, int available, String itemName) {
        if (available < requestedQuantity) {
            throw new BusinessRuleException("Only " + available + " unit(s) of \"" + itemName + "\" available");
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

        return cartMapper.toCartResponse(items, productThumbnails, cropListingThumbnails);
    }
}
