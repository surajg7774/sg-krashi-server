package com.sgkrashi.dairy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Extra facts about a dairy product (one row per dairy product, keyed by the product id). */
@Entity
@Table(name = "product_dairy_details")
public class ProductDairyDetails {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    private DairyUnit unit;

    @Column(name = "pack_size", nullable = false, precision = 10, scale = 3)
    private BigDecimal packSize;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "fresh_daily", nullable = false)
    private boolean freshDaily;

    @Column(name = "storage_note", length = 500)
    private String storageNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public DairyUnit getUnit() { return unit; }
    public void setUnit(DairyUnit unit) { this.unit = unit; }
    public BigDecimal getPackSize() { return packSize; }
    public void setPackSize(BigDecimal packSize) { this.packSize = packSize; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
    public boolean isFreshDaily() { return freshDaily; }
    public void setFreshDaily(boolean freshDaily) { this.freshDaily = freshDaily; }
    public String getStorageNote() { return storageNote; }
    public void setStorageNote(String storageNote) { this.storageNote = storageNote; }
}
