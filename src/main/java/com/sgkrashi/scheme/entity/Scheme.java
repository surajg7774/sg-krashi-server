package com.sgkrashi.scheme.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A government scheme/subsidy reference entry — see V31 migration for why this is a dedicated table, not a CMS ContentBlockType. */
@Entity
@Table(name = "schemes")
public class Scheme extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private SchemeCategory category;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "eligibility", nullable = false, columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "benefit", nullable = false, columnDefinition = "TEXT")
    private String benefit;

    @Column(name = "official_link", nullable = false, length = 500)
    private String officialLink;

    @Column(name = "state_scope", length = 100)
    private String stateScope;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SchemeCategory getCategory() {
        return category;
    }

    public void setCategory(SchemeCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEligibility() {
        return eligibility;
    }

    public void setEligibility(String eligibility) {
        this.eligibility = eligibility;
    }

    public String getBenefit() {
        return benefit;
    }

    public void setBenefit(String benefit) {
        this.benefit = benefit;
    }

    public String getOfficialLink() {
        return officialLink;
    }

    public void setOfficialLink(String officialLink) {
        this.officialLink = officialLink;
    }

    public String getStateScope() {
        return stateScope;
    }

    public void setStateScope(String stateScope) {
        this.stateScope = stateScope;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
