package com.platform.ecommerce.product.repository;

import com.platform.ecommerce.product.entity.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    List<ProductDocument> findByNameContainingOrDescriptionContainingAndActiveTrue(String name, String description);
    List<ProductDocument> findByCategoryAndActiveTrue(String category);
}
