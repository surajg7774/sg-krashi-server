package com.sgkrashi.order.mapper;

import com.sgkrashi.customer.dto.response.CustomerProfileResponse;
import com.sgkrashi.customer.mapper.CustomerProfileMapper;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.order.dto.response.AdminOrderDetailResponse;
import com.sgkrashi.order.dto.response.AdminOrderSummaryResponse;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An order outlives its customer's account. After deletion the users row is
 * kept (anonymized) and the order's street lines are blanked — these tests
 * confirm the admin screens still render such an order, showing "Deleted
 * user", with no nulls where the UI expects text.
 */
class RetainedOrderAfterAccountDeletionTest {

    /** The same values AccountErasureRepository#erase writes. */
    private static User anonymizedUser(long id) {
        User u = new User();
        u.setId(id);
        u.setName("Deleted user");
        u.setEmail("deleted-" + id + "@deleted.invalid");
        u.setPhone(null);
        u.setPasswordHash(null);
        u.setGoogleId(null);
        return u;
    }

    private static Order retainedOrder(long userId) {
        Order o = new Order();
        o.setUserId(userId);
        o.setOrderNumber("SGK-1001");
        o.setStatus(OrderStatus.DELIVERED);
        o.setTotalAmount(new BigDecimal("1499.00"));
        o.setShippingLine1("[removed]");   // what erase() leaves behind
        o.setShippingLine2(null);
        o.setShippingCity("Khandwa");      // kept: needed for tax/accounting
        o.setShippingState("Madhya Pradesh");
        o.setShippingPincode("450001");
        return o;
    }

    @Test
    void theAdminOrderDetailStillRendersAndShowsDeletedUser() {
        User gone = anonymizedUser(42);
        AdminOrderDetailResponse detail = new OrderMapper().toAdminDetailResponse(
                retainedOrder(42), List.of(), List.of(), Map.of(), Map.of(),
                gone.getName(), gone.getEmail(), false, null, Map.of(), null, null);

        assertEquals("Deleted user", detail.userName());
        assertEquals("deleted-42@deleted.invalid", detail.userEmail());
        assertEquals("[removed]", detail.shippingLine1());
        assertNull(detail.shippingLine2());
        assertEquals("Khandwa", detail.shippingCity());
        assertEquals(new BigDecimal("1499.00"), detail.totalAmount());
        assertEquals(OrderStatus.DELIVERED, detail.status());
    }

    @Test
    void theAdminOrderListStillRenders() {
        User gone = anonymizedUser(42);
        AdminOrderSummaryResponse summary = new OrderMapper().toAdminSummaryResponse(
                retainedOrder(42), 2, gone.getName(), gone.getEmail(), false, null, false);

        assertEquals("Deleted user", summary.userName());
        assertEquals(42L, summary.userId());
        assertEquals("SGK-1001", summary.orderNumber());
    }

    @Test
    void anAnonymizedAccountReportsNoSignInMethodsAndNeverExposesHashes() {
        Role customer = new Role();
        customer.setName("CUSTOMER");
        User gone = anonymizedUser(42);
        gone.setRoles(Set.of(customer));

        CustomerProfileResponse profile = new CustomerProfileMapper().toResponse(gone);

        assertFalse(profile.hasPassword());
        assertFalse(profile.googleLinked());
        assertTrue(profile.roles().contains("CUSTOMER"));
    }
}
