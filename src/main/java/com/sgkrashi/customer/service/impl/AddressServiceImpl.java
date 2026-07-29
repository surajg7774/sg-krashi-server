package com.sgkrashi.customer.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.customer.dto.request.AddressRequest;
import com.sgkrashi.customer.dto.response.AddressResponse;
import com.sgkrashi.customer.entity.Address;
import com.sgkrashi.customer.mapper.AddressMapper;
import com.sgkrashi.customer.repository.AddressRepository;
import com.sgkrashi.customer.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AddressMapper addressMapper;

    public AddressServiceImpl(
            AddressRepository addressRepository,
            CurrentUserProvider currentUserProvider,
            AddressMapper addressMapper
    ) {
        this.addressRepository = addressRepository;
        this.currentUserProvider = currentUserProvider;
        this.addressMapper = addressMapper;
    }

    @Override
    public List<AddressResponse> listAddresses() {
        Long userId = currentUserProvider.getCurrentUserId();
        return addressRepository.findByUserIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtAsc(userId).stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse createAddress(AddressRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        boolean isFirstAddress = addressRepository
                .findByUserIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtAsc(userId).isEmpty();

        Address address = new Address();
        address.setUserId(userId);
        applyRequest(address, request);
        address.setDefault(isFirstAddress);

        Address saved = addressRepository.save(address);
        return addressMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long addressId, AddressRequest request) {
        Address address = getOwnedAddressOrThrow(addressId);
        applyRequest(address, request);
        Address saved = addressRepository.save(address);
        return addressMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteAddress(Long addressId) {
        Address address = getOwnedAddressOrThrow(addressId);
        boolean wasDefault = address.isDefault();
        address.setActive(false);
        address.setDefault(false);
        addressRepository.save(address);

        if (wasDefault) {
            Long userId = currentUserProvider.getCurrentUserId();
            addressRepository.findByUserIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtAsc(userId).stream()
                    .max(Comparator.comparing(Address::getCreatedAt))
                    .ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(Long addressId) {
        Address address = getOwnedAddressOrThrow(addressId);
        Long userId = currentUserProvider.getCurrentUserId();

        addressRepository.findByUserIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtAsc(userId).forEach(existing -> {
            if (existing.isDefault() && !existing.getId().equals(address.getId())) {
                existing.setDefault(false);
                addressRepository.save(existing);
            }
        });

        address.setDefault(true);
        Address saved = addressRepository.save(address);
        return addressMapper.toResponse(saved);
    }

    /**
     * Verify address belongs to authenticated user — never trust the path param alone.
     * A mismatch surfaces as 404 (via ResourceNotFoundException) rather than 403, so a
     * caller can't distinguish "doesn't exist" from "exists but isn't yours."
     */
    private Address getOwnedAddressOrThrow(Long addressId) {
        Address address = addressRepository.findById(addressId)
                .filter(Address::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        Long userId = currentUserProvider.getCurrentUserId();
        if (!address.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address not found");
        }
        return address;
    }

    private void applyRequest(Address address, AddressRequest request) {
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
    }
}
