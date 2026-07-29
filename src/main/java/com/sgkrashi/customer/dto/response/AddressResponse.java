package com.sgkrashi.customer.dto.response;

public record AddressResponse(
        Long id,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        boolean isDefault
) {
}
