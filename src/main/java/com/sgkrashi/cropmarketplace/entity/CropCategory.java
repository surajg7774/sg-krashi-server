package com.sgkrashi.cropmarketplace.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Flat (no parent/child) unlike {@code ProductCategory} — the crop marketplace
 * doesn't need a category tree UI (Section 4.1 of the Module 7 prompt), so
 * there's no hierarchy to model. See {@code CropListingSpecifications} for
 * how this doubles as the "crop type" filter facet.
 */
@Entity
@Table(name = "crop_categories")
public class CropCategory extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 170)
    private String slug;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
