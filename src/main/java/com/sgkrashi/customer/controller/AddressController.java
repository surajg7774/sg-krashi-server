package com.sgkrashi.customer.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.customer.dto.request.AddressRequest;
import com.sgkrashi.customer.dto.response.AddressResponse;
import com.sgkrashi.customer.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The authenticated user's own address book. Every mutation verifies
 * ownership in the service layer before touching the row — see
 * {@code AddressServiceImpl.getOwnedAddressOrThrow}.
 */
@RestController
@RequestMapping("/api/v1/customers/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(addressService.listAddresses(), "Addresses retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> create(@Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.createAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Address added"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request
    ) {
        AddressResponse response = addressService.updateAddress(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Address updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Address deleted"));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefault(@PathVariable Long id) {
        AddressResponse response = addressService.setDefaultAddress(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Default address updated"));
    }
}
