package com.sgkrashi.order.entity;

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

import java.math.BigDecimal;

/**
 * Line item of a placed order. {@code unitPriceSnapshot} and
 * {@code itemNameSnapshot} are captured at checkout time and must NEVER be
 * recomputed from the live {@code Product}/{@code CropListing} row afterward
 * — a later price change or rename must not alter the historical amount the
 * customer was charged.
 *
 * <p>Generalized in Module 7 to reference either a {@link Product} or a
 * {@link CropListing}, exactly like {@code CartItem} — same
 * exactly-one-of-product-or-cropListing invariant, same reasoning.
 * {@code itemNameSnapshot} keeps its underlying {@code product_name_snapshot}
 * column name (no migration rename) to minimize risk to Module 6's existing
 * data; only the Java-level name changed.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_listing_id")
    private CropListing cropListing;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String itemNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
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

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public void setItemNameSnapshot(String itemNameSnapshot) {
        this.itemNameSnapshot = itemNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    /** The product's or crop listing's ID, whichever applies — never triggers lazy-loading beyond the FK. */
    public Long getReferencedItemId() {
        return itemType == ItemType.PRODUCT ? product.getId() : cropListing.getId();
    }
}
