package com.sgkrashi.dairy.dto;

import com.sgkrashi.dairy.entity.DairyUnit;
import com.sgkrashi.dairy.entity.SubscriptionFrequency;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Request/response shapes for everything dairy, kept together because they are small and only used by this module. */
public final class DairyDtos {

    private DairyDtos() {
    }

    // ---- product details ----

    public record DairyDetailsRequest(
            @NotNull(message = "Unit is required") DairyUnit unit,
            @NotNull(message = "Pack size is required")
            @DecimalMin(value = "0.001", message = "Pack size must be greater than zero")
            @DecimalMax(value = "100000", message = "Pack size is too large")
            BigDecimal packSize,
            @Min(value = 1, message = "Shelf life must be at least 1 day") @Max(value = 3650, message = "Shelf life is too long")
            Integer shelfLifeDays,
            boolean freshDaily,
            @Size(max = 500, message = "Storage note must be at most 500 characters") String storageNote
    ) {
    }

    public record DairyDetailsResponse(
            DairyUnit unit, BigDecimal packSize, Integer shelfLifeDays, boolean freshDaily, String storageNote
    ) {
    }

    /** A dairy product as the Dairy page lists it: the usual store card fields plus the dairy facts. */
    public record DairyProductResponse(ProductSummaryResponse product, DairyDetailsResponse dairy) {
    }

    // ---- public configuration and delivery options ----

    public record DairyConfigResponse(boolean subscriptionsEnabled, boolean deliveryRestricted, boolean hasDeliverySlots) {
    }

    public record SlotOption(Long id, String name, LocalTime startTime, LocalTime endTime, List<LocalDate> dates) {
    }

    /**
     * {@code restricted}: the farm has listed delivery pincodes. {@code serviceable} is null when no pincode was asked about;
     * with no listed pincodes it is always true. {@code slots} are empty when no slots are configured.
     */
    public record DeliveryOptionsResponse(boolean restricted, Boolean serviceable, List<SlotOption> slots) {
    }

    public record OrderDeliveryResponse(
            Long slotId, String slotName, LocalTime slotStart, LocalTime slotEnd, LocalDate deliveryDate
    ) {
    }

    // ---- admin: areas and slots ----

    public record DeliveryAreaRequest(
            @NotBlank(message = "Pincode is required")
            @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Pincode must be 6 digits")
            String pincode,
            @Size(max = 100, message = "Label must be at most 100 characters") String label,
            Boolean isActive
    ) {
    }

    public record DeliveryAreaResponse(Long id, String pincode, String label, boolean isActive) {
    }

    public record DeliverySlotRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime,
            @NotEmpty(message = "Choose at least one day") Set<DayOfWeek> days,
            Integer sortOrder,
            Boolean isActive
    ) {
    }

    public record DeliverySlotResponse(
            Long id, String name, LocalTime startTime, LocalTime endTime, List<DayOfWeek> days, int sortOrder, boolean isActive
    ) {
    }

    // ---- subscriptions: customer ----

    public record CreateSubscriptionRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be at least 1") Integer quantity,
            @NotNull(message = "Frequency is required") SubscriptionFrequency frequency,
            Set<DayOfWeek> weekdays,
            @NotNull(message = "Start date is required") LocalDate startDate,
            Long slotId,
            @NotNull(message = "Delivery address is required") Long addressId
    ) {
    }

    public record PauseRequest(@NotNull(message = "Pause start date is required") LocalDate from, LocalDate to) {
    }

    public record SkipRequest(@NotNull(message = "Date is required") LocalDate date) {
    }

    /** state: UPCOMING, SCHEDULED (prepared), SKIPPED_BY_CUSTOMER, SKIPPED_OUT_OF_STOCK, DELIVERED or FAILED. */
    public record UpcomingDelivery(LocalDate date, String state) {
    }

    public record SubscriptionResponse(
            Long id,
            Long productId,
            String productName,
            BigDecimal currentUnitPrice,
            int quantity,
            SubscriptionFrequency frequency,
            List<DayOfWeek> weekdays,
            LocalDate startDate,
            Long slotId,
            String slotName,
            LocalTime slotStart,
            LocalTime slotEnd,
            String status,
            LocalDate pauseFrom,
            LocalDate pauseTo,
            List<LocalDate> skipDates,
            String addressLine1,
            String addressLine2,
            String addressCity,
            String addressState,
            String addressPincode,
            List<UpcomingDelivery> upcoming,
            Instant createdAt
    ) {
    }

    public record DeliveryHistoryItem(
            Long id, LocalDate date, int quantity, String productName, BigDecimal lineTotal, String status, boolean cashCollected
    ) {
    }

    // ---- admin: subscriptions and deliveries ----

    public record AdminSubscriptionResponse(
            Long id,
            Long userId,
            String customerName,
            String customerEmail,
            String customerPhone,
            Long productId,
            String productName,
            int quantity,
            SubscriptionFrequency frequency,
            List<DayOfWeek> weekdays,
            LocalDate startDate,
            String slotName,
            String status,
            LocalDate pauseFrom,
            LocalDate pauseTo,
            String addressPincode,
            Instant createdAt
    ) {
    }

    public record AdminDeliveryRow(
            Long id,
            Long subscriptionId,
            LocalDate date,
            String productName,
            int quantity,
            String slotName,
            LocalTime slotStart,
            LocalTime slotEnd,
            String customerName,
            String customerPhone,
            String addressLine1,
            String addressLine2,
            String addressCity,
            String addressState,
            String addressPincode,
            BigDecimal lineTotal,
            String status,
            boolean cashCollected,
            String note
    ) {
    }

    public record MarkDeliveredRequest(Boolean cashCollected) {
    }

    public record MarkFailedRequest(@Size(max = 255, message = "Reason must be at most 255 characters") String reason) {
    }
}
