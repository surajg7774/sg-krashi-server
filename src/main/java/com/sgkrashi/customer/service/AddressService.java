package com.sgkrashi.customer.service;

import com.sgkrashi.customer.dto.request.AddressRequest;
import com.sgkrashi.customer.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {

    /** Lists the authenticated user's own active addresses, default-first. */
    List<AddressResponse> listAddresses();

    /** Creates an address for the authenticated user. The first address ever added becomes the default automatically. */
    AddressResponse createAddress(AddressRequest request);

    /**
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the address doesn't exist
     * or doesn't belong to the authenticated user (same response either way, to avoid confirming
     * another user's address ID exists)
     */
    AddressResponse updateAddress(Long addressId, AddressRequest request);

    /**
     * Soft-deletes the address ({@code is_active = false}). If it was the default and other
     * addresses remain, the most recently created remaining one is promoted to default.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if not owned by the authenticated user
     */
    void deleteAddress(Long addressId);

    /**
     * Sets the given address as default, atomically unsetting whatever was
     * previously the default for this user.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if not owned by the authenticated user
     */
    AddressResponse setDefaultAddress(Long addressId);
}
