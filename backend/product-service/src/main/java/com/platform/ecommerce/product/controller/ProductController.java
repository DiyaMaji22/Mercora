package com.platform.ecommerce.product.controller;

import com.platform.ecommerce.product.dto.ProductDtos.CreateProductRequest;
import com.platform.ecommerce.product.dto.ProductDtos.ProductResponse;
import com.platform.ecommerce.product.dto.ProductDtos.UpdateProductRequest;
import com.platform.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<ProductResponse> create(Authentication auth,
                                                    @Valid @RequestBody CreateProductRequest request) {
        UUID retailerId = UUID.fromString(String.valueOf(auth.getDetails()));
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request, retailerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id,
                                                    @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(productService.search(q, category));
    }
}
