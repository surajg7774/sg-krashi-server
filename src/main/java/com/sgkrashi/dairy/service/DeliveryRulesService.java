package com.sgkrashi.dairy.service;

import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.dairy.config.DairyProperties;
import com.sgkrashi.dairy.config.DairyTime;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryOptionsResponse;
import com.sgkrashi.dairy.dto.DairyDtos.SlotOption;
import com.sgkrashi.dairy.entity.DeliverySlot;
import com.sgkrashi.dairy.repository.DeliveryAreaRepository;
import com.sgkrashi.dairy.repository.DeliverySlotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The admin-managed delivery rules. Nothing is assumed: with no delivery areas listed there is no pincode restriction,
 * and with no slots listed there is nothing for a customer to choose.
 */
@Service
public class DeliveryRulesService {

    /** The slot and date a customer picked, after they have been checked. */
    public record ChosenDelivery(DeliverySlot slot, LocalDate date) {
    }

    private final DeliveryAreaRepository areaRepository;
    private final DeliverySlotRepository slotRepository;
    private final DairyProperties properties;
    private final DairyTime time;

    public DeliveryRulesService(
            DeliveryAreaRepository areaRepository,
            DeliverySlotRepository slotRepository,
            DairyProperties properties,
            DairyTime time
    ) {
        this.areaRepository = areaRepository;
        this.slotRepository = slotRepository;
        this.properties = properties;
        this.time = time;
    }

    public boolean isRestricted() {
        return areaRepository.countByIsActiveTrue() > 0;
    }

    public boolean serves(String pincode) {
        if (!isRestricted()) {
            return true;
        }
        return pincode != null && areaRepository.existsByIsActiveTrueAndPincode(pincode.trim());
    }

    public List<DeliverySlot> activeSlots() {
        return slotRepository.findByIsActiveTrueOrderBySortOrderAscStartTimeAsc();
    }

    /** Dates a one-time dairy order can be delivered on for this slot: from the earliest allowed date, on the slot's days. */
    public List<LocalDate> availableDates(DeliverySlot slot) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate first = time.today().plusDays(properties.minLeadDays());
        for (int i = 0; i < properties.bookingWindowDays(); i++) {
            LocalDate date = first.plusDays(i);
            if (slot.deliversOn(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }

    public DeliveryOptionsResponse options(String pincode) {
        boolean restricted = isRestricted();
        Boolean serviceable = pincode == null || pincode.isBlank() ? null : serves(pincode);
        List<SlotOption> slots = activeSlots().stream()
                .map(slot -> new SlotOption(slot.getId(), slot.getName(), slot.getStartTime(), slot.getEndTime(), availableDates(slot)))
                .toList();
        return new DeliveryOptionsResponse(restricted, serviceable, slots);
    }

    /**
     * Checks a dairy checkout: the pincode must be served (when areas are listed) and, when slots exist, a slot and one
     * of its available dates must be chosen. Returns null when there is no slot to record.
     */
    public ChosenDelivery validateForCheckout(String pincode, Long slotId, LocalDate date) {
        if (!serves(pincode)) {
            throw new BusinessRuleException("Sorry, we don't deliver dairy to pincode " + (pincode == null ? "" : pincode.trim()) + " yet.");
        }
        List<DeliverySlot> slots = activeSlots();
        if (slots.isEmpty()) {
            return null;
        }
        if (slotId == null || date == null) {
            throw new BusinessRuleException("Please choose a delivery slot and date for the dairy items in your cart.");
        }
        DeliverySlot slot = slots.stream().filter(s -> s.getId().equals(slotId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("That delivery slot is not available. Please choose another."));
        if (!availableDates(slot).contains(date)) {
            throw new BusinessRuleException("That delivery date is not available for the chosen slot. Please choose another.");
        }
        return new ChosenDelivery(slot, date);
    }
}
