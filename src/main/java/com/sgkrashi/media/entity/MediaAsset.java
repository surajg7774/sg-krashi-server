package com.sgkrashi.media.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A stored image/file associated with some other entity (a product, a future
 * crop listing, equipment, a stay, etc.) via {@link #ownerType}/{@link #ownerId}.
 *
 * <p>This is a deliberate polymorphic-lite association with no foreign key on
 * {@code owner_id} — it can't reference one table since it points at different
 * entity types depending on {@code owner_type}. The tradeoff: we lose database-
 * level referential integrity (a dangling owner_id after its owner is hard-deleted
 * would leave an orphaned row), in exchange for not needing a separate media table
 * per owner type as new modules (crop listings, equipment, stays) add their own
 * media. Owners in this codebase are soft-deleted (is_active = false), so orphaned
 * rows from a real delete shouldn't occur in practice.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset extends BaseEntity {

    @Column(name = "owner_type", nullable = false, length = 50)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
