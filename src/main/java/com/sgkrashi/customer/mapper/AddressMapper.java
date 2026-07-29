package com.sgkrashi.customer.mapper;

import com.sgkrashi.customer.dto.response.AddressResponse;
import com.sgkrashi.customer.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.isDefault()
        );
    }
}
