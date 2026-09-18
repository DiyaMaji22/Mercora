package com.platform.ecommerce.product.service;

import com.platform.ecommerce.product.dto.ProductDtos.CreateProductRequest;
import com.platform.ecommerce.product.dto.ProductDtos.ProductResponse;
import com.platform.ecommerce.product.dto.ProductDtos.UpdateProductRequest;
import com.platform.ecommerce.product.entity.Product;
import com.platform.ecommerce.product.entity.ProductDocument;
import com.platform.ecommerce.product.exception.DuplicateSkuException;
import com.platform.ecommerce.product.exception.ProductNotFoundException;
import com.platform.ecommerce.product.repository.ProductRepository;
import com.platform.ecommerce.product.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;

    @Transactional
    public ProductResponse create(CreateProductRequest request, UUID retailerId) {
        if (productRepository.findBySku(request.sku()).isPresent()) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .price(request.price())
                .retailerId(retailerId)
                .active(true)
                .build();

        Product saved = productRepository.save(product);
        syncToSearchIndex(saved);
        return toResponse(saved);
    }

    /** cache:product:{id}, 5-minute TTL (see RedisConfig). */
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return toResponse(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.category() != null) product.setCategory(request.category());
        if (request.price() != null) product.setPrice(request.price());

        Product saved = productRepository.save(product);
        syncToSearchIndex(saved);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deactivate(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.setActive(false);
        productRepository.save(product);
        syncToSearchIndex(product);
    }

    public List<ProductResponse> search(String query, String category) {
        if (query != null && !query.isBlank()) {
            List<ProductDocument> results = productSearchRepository
                    .findByNameContainingOrDescriptionContainingAndActiveTrue(query, query);
            return results.stream()
                    .map(doc -> productRepository.findById(UUID.fromString(doc.getId())))
                    .flatMap(java.util.Optional::stream)
                    .map(this::toResponse)
                    .toList();
        } else if (category != null && !category.isBlank()) {
            List<ProductDocument> results = productSearchRepository.findByCategoryAndActiveTrue(category);
            return results.stream()
                    .map(doc -> productRepository.findById(UUID.fromString(doc.getId())))
                    .flatMap(java.util.Optional::stream)
                    .map(this::toResponse)
                    .toList();
        } else {
            return productRepository.findAllByActiveTrue(
                            PageRequest.of(0, 100, Sort.by("updatedAt").descending()))
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
    }

    private void syncToSearchIndex(Product product) {
        try {
            productSearchRepository.save(ProductDocument.builder()
                    .id(product.getId().toString())
                    .name(product.getName())
                    .description(product.getDescription())
                    .category(product.getCategory())
                    .price(product.getPrice())
                    .active(product.isActive())
                    .build());
        } catch (Exception e) {
            // Search index sync failures shouldn't fail the write path to
            // PostgreSQL (the source of truth) - log and let a periodic
            // reindex job (not shown) reconcile.
            log.warn("Failed to sync product {} to search index: {}", product.getId(), e.getMessage());
        }
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory(), p.getPrice(), p.getRetailerId(), p.isActive(), p.getUpdatedAt());
    }
}
