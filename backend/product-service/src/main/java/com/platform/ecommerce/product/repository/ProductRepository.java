package com.platform.ecommerce.product.repository;

import com.platform.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);
    List<Product> findByRetailerId(UUID retailerId);
    List<Product> findByCategoryAndActiveTrue(String category);
    Page<Product> findAllByActiveTrue(Pageable pageable);
}
