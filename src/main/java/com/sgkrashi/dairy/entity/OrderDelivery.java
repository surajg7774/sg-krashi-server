package com.sgkrashi.dairy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** The delivery slot and date chosen for an order that contains dairy. */
@Entity
@Table(name = "order_delivery")
public class OrderDelivery {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "slot_name", length = 100)
    private String slotName;

    @Column(name = "slot_start")
    private LocalTime slotStart;

    @Column(name = "slot_end")
    private LocalTime slotEnd;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public String getSlotName() { return slotName; }
    public void setSlotName(String slotName) { this.slotName = slotName; }
    public LocalTime getSlotStart() { return slotStart; }
    public void setSlotStart(LocalTime slotStart) { this.slotStart = slotStart; }
    public LocalTime getSlotEnd() { return slotEnd; }
    public void setSlotEnd(LocalTime slotEnd) { this.slotEnd = slotEnd; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
}
