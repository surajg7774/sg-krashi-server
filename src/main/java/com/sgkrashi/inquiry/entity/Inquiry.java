package com.sgkrashi.inquiry.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A guest or user submission for any module's inquiry/contact path. Started
 * in Module 3 as a minimal {@code GENERAL} contact form; Module 10 formalized
 * {@link #moduleType} and {@link #status} as real enums, added the optional
 * {@link #preferredDate}/{@link #groupSize} fields for visit-style inquiries
 * (Organic Farming), and the full {@link InquiryStatus} workflow. Adding a new
 * module's inquiry support (e.g. Module 11's Dairy Farm) should only require a
 * new {@link InquiryModuleType} constant — no structural change here.
 */
@Entity
@Table(name = "inquiries")
public class Inquiry extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 50)
    private InquiryModuleType moduleType;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Visit date requested by the inquirer, e.g. an Organic Farming farm visit. Null for a general contact submission. */
    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    /** Party size for a visit/wholesale inquiry. Null for a general contact submission. */
    @Column(name = "group_size")
    private Integer groupSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InquiryStatus status;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public InquiryModuleType getModuleType() {
        return moduleType;
    }

    public void setModuleType(InquiryModuleType moduleType) {
        this.moduleType = moduleType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDate getPreferredDate() {
        return preferredDate;
    }

    public void setPreferredDate(LocalDate preferredDate) {
        this.preferredDate = preferredDate;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public void setStatus(InquiryStatus status) {
        this.status = status;
    }
}
