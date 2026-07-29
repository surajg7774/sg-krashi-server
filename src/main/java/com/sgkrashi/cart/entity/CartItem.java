package com.sgkrashi.cart.entity;

import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.productstore.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A line in a {@link Cart}. Deliberately does NOT extend {@code BaseEntity}
 * and is hard-deleted on removal/checkout rather than soft-deleted — unlike
 * addresses or products, nobody needs an audit trail of items removed from an
 * in-progress cart, and hard delete avoids the unique (cart_id, product_id)
 * constraint colliding with a soft-deleted row if the same product is added
 * again later.
 *
 * <p>Generalized in Module 7 to reference either a {@link Product} or a
 * {@link CropListing}: exactly one of {@link #product}/{@link #cropListing}
 * is populated, per {@link #itemType} — enforced in {@code CartServiceImpl},
 * not at the DB level (see the Module 7 report for why a CHECK constraint
 * was skipped).
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_listing_id")
    private CropListing cropListing;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public CropListing getCropListing() {
        return cropListing;
    }

    public void setCropListing(CropListing cropListing) {
        this.cropListing = cropListing;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /** The product's or crop listing's ID, whichever applies — never triggers lazy-loading beyond the FK. */
    public Long getReferencedItemId() {
        return itemType == ItemType.PRODUCT ? product.getId() : cropListing.getId();
    }
}
