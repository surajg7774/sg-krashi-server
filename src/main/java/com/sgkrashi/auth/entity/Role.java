package com.sgkrashi.auth.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A named role granted to users (e.g. CUSTOMER, ADMIN, SUPER_ADMIN).
 * Rows are seeded by the {@code V2__auth_tables.sql} migration; no create/delete
 * API exists for roles until role management ships in Module 14.
 */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
