package com.sgkrashi.dairy.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A customer's standing order for one dairy product. The delivery address is copied onto it (like an order's shipping
 * address) so deleting or editing a saved address later cannot change where the milk goes.
 *
 * <p>Whether a given date is a delivery day is decided in one place, {@link #deliversOn(LocalDate)}, so the nightly job
 * and the "upcoming deliveries" screen can never disagree.
 */
@Entity
@Table(name = "dairy_subscriptions")
public class DairySubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private SubscriptionFrequency frequency;

    /** Comma list such as MON,WED,FRI; only used when the frequency is DAYS. */
    @Column(name = "weekdays", length = 40)
    private String weekdays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "slot_id")
    private Long slotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** A pause covers pauseFrom..pauseTo inclusive; a null pauseTo means "until resumed". */
    @Column(name = "pause_from")
    private LocalDate pauseFrom;

    @Column(name = "pause_to")
    private LocalDate pauseTo;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "address_city", nullable = false, length = 100)
    private String addressCity;

    @Column(name = "address_state", nullable = false, length = 100)
    private String addressState;

    @Column(name = "address_pincode", nullable = false, length = 10)
    private String addressPincode;

    @ElementCollection
    @CollectionTable(name = "dairy_subscription_skips", joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "skip_date", nullable = false)
    private Set<LocalDate> skipDates = new HashSet<>();

    public boolean isPausedOn(LocalDate date) {
        return pauseFrom != null && !date.isBefore(pauseFrom) && (pauseTo == null || !date.isAfter(pauseTo));
    }

    /**
     * Is {@code date} a day this subscription should receive a delivery? Pure: depends only on the subscription's own
     * fields. Cancelled subscriptions never deliver; before the start date nothing is delivered; a paused or skipped
     * date is not a delivery day.
     */
    public boolean deliversOn(LocalDate date) {
        if (status == SubscriptionStatus.CANCELLED || date.isBefore(startDate)) {
            return false;
        }
        if (frequency == SubscriptionFrequency.DAYS && !weekdaySet().contains(date.getDayOfWeek())) {
            return false;
        }
        if (isPausedOn(date)) {
            return false;
        }
        return !skipDates.contains(date);
    }

    /** What to show the customer today: a pause that has ended no longer reads as PAUSED. */
    public SubscriptionStatus effectiveStatus(LocalDate today) {
        if (status == SubscriptionStatus.CANCELLED) {
            return SubscriptionStatus.CANCELLED;
        }
        if (status == SubscriptionStatus.PAUSED && pauseTo != null && today.isAfter(pauseTo)) {
            return SubscriptionStatus.ACTIVE;
        }
        return status;
    }

    public Set<DayOfWeek> weekdaySet() {
        return DeliverySlot.WeekdayCodes.parse(weekdays);
    }

    public void setWeekdaySet(Set<DayOfWeek> days) {
        this.weekdays = days == null || days.isEmpty() ? null : DeliverySlot.WeekdayCodes.format(days);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public SubscriptionFrequency getFrequency() { return frequency; }
    public void setFrequency(SubscriptionFrequency frequency) { this.frequency = frequency; }
    public String getWeekdays() { return weekdays; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public LocalDate getPauseFrom() { return pauseFrom; }
    public void setPauseFrom(LocalDate pauseFrom) { this.pauseFrom = pauseFrom; }
    public LocalDate getPauseTo() { return pauseTo; }
    public void setPauseTo(LocalDate pauseTo) { this.pauseTo = pauseTo; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String addressCity) { this.addressCity = addressCity; }
    public String getAddressState() { return addressState; }
    public void setAddressState(String addressState) { this.addressState = addressState; }
    public String getAddressPincode() { return addressPincode; }
    public void setAddressPincode(String addressPincode) { this.addressPincode = addressPincode; }
    public Set<LocalDate> getSkipDates() { return skipDates; }
    public void setSkipDates(Set<LocalDate> skipDates) { this.skipDates = skipDates; }
}
