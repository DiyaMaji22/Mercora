package com.platform.ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

/**
 * Search-optimized projection of Product, synced on write (see
 * ProductService.save -> productSearchRepository.save). PostgreSQL remains
 * the system of record; Elasticsearch here only serves full-text catalog
 * search so a reindex from Postgres can always rebuild it if it drifts.
 */
@Document(indexName = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Boolean)
    private boolean active;
}
