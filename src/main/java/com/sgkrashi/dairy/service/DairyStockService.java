package com.sgkrashi.dairy.service;

import com.sgkrashi.dairy.entity.DairyDelivery;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.stereotype.Component;

/** Gives units held by a prepared delivery back to the product's stock. Must run inside the caller's transaction. */
@Component
public class DairyStockService {

    private final ProductRepository productRepository;

    public DairyStockService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Returns the delivery's units to stock once (a delivery that holds none, or already gave them back, is left alone). */
    public void restore(DairyDelivery delivery) {
        if (!delivery.isStockReserved()) {
            return;
        }
        Product product = productRepository.findByIdForUpdate(delivery.getProductId()).orElse(null);
        if (product != null) {
            product.setStockQty(product.getStockQty() + delivery.getQuantity());
            productRepository.save(product);
        }
        delivery.setStockReserved(false);
    }
}
