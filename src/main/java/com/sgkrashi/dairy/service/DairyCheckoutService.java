package com.sgkrashi.dairy.service;

import com.sgkrashi.dairy.dto.DairyDtos.OrderDeliveryResponse;
import com.sgkrashi.dairy.entity.OrderDelivery;
import com.sgkrashi.dairy.repository.OrderDeliveryRepository;
import com.sgkrashi.dairy.service.DeliveryRulesService.ChosenDelivery;
import com.sgkrashi.productstore.entity.Product;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * The dairy part of checkout. A cart with no dairy item goes through {@link #validate} untouched: no pincode check, no
 * slot, nothing saved, so every non-dairy order behaves exactly as it did before dairy existed.
 */
@Service
public class DairyCheckoutService {

    private final DairyCatalogService catalogService;
    private final DeliveryRulesService rulesService;
    private final OrderDeliveryRepository orderDeliveryRepository;

    public DairyCheckoutService(
            DairyCatalogService catalogService,
            DeliveryRulesService rulesService,
            OrderDeliveryRepository orderDeliveryRepository
    ) {
        this.catalogService = catalogService;
        this.rulesService = rulesService;
        this.orderDeliveryRepository = orderDeliveryRepository;
    }

    /** Throws a business-rule error for a dairy cart that cannot be delivered as asked; returns the chosen slot, or null. */
    public ChosenDelivery validate(Collection<Product> products, String pincode, Long slotId, LocalDate date) {
        Set<Long> dairyIds = catalogService.dairyCategoryIds();
        if (dairyIds.isEmpty() || products.stream().noneMatch(p -> catalogService.isDairy(p, dairyIds))) {
            return null;
        }
        return rulesService.validateForCheckout(pincode, slotId, date);
    }

    public void record(Long orderId, ChosenDelivery chosen) {
        if (chosen == null) {
            return;
        }
        OrderDelivery delivery = new OrderDelivery();
        delivery.setOrderId(orderId);
        delivery.setSlotId(chosen.slot().getId());
        delivery.setSlotName(chosen.slot().getName());
        delivery.setSlotStart(chosen.slot().getStartTime());
        delivery.setSlotEnd(chosen.slot().getEndTime());
        delivery.setDeliveryDate(chosen.date());
        orderDeliveryRepository.save(delivery);
    }

    public Optional<OrderDeliveryResponse> findForOrder(Long orderId) {
        return orderDeliveryRepository.findById(orderId).map(d -> new OrderDeliveryResponse(
                d.getSlotId(), d.getSlotName(), d.getSlotStart(), d.getSlotEnd(), d.getDeliveryDate()));
    }
}
