package com.interview.catalog.web;

import com.interview.catalog.model.CreateProductRequest;
import com.interview.catalog.model.Product;
import com.interview.catalog.model.UpdateProductRequest;
import com.interview.catalog.service.ProductService;
import com.interview.catalog.service.ServiceResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/products")
    public ResponseEntity<?> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return toHttp(service.list(category, page, pageSize));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable String id) {
        return toHttp(service.get(id));
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody CreateProductRequest request) {
        return toHttp(service.create(request));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable String id, @RequestBody UpdateProductRequest request) {
        return toHttp(service.update(id, request));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable String id) {
        return toHttp(service.delete(id));
    }

    @PostMapping("/products/{id}/image")
    public ResponseEntity<?> attachImage(
            @PathVariable String id,
            @RequestBody byte[] content,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Content-Type", defaultValue = "application/octet-stream") String contentType) {
        return toHttp(service.attachImage(id, content, contentType));
    }

    private static <T> ResponseEntity<?> toHttp(ServiceResult<T> result) {
        return switch (result.getStatus()) {
            case 200 -> ResponseEntity.ok(result.getValue());
            case 201 -> {
                URI location = URI.create("/products/" + (result.getValue() instanceof Product p ? p.getId() : ""));
                yield ResponseEntity.created(location).body(result.getValue());
            }
            case 204 -> ResponseEntity.noContent().build();
            case 400 -> ResponseEntity.badRequest().body(Map.of("error", result.getError()));
            case 404 -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", result.getError()));
            default -> ResponseEntity.status(result.getStatus()).build();
        };
    }
}