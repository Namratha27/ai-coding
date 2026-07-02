package com.interview.catalog.service;

import com.interview.catalog.model.CreateProductRequest;
import com.interview.catalog.model.Product;
import com.interview.catalog.model.UpdateProductRequest;
import com.interview.catalog.simulated.SimulatedBlobContainer;
import com.interview.catalog.simulated.SimulatedCosmosContainer;
import com.interview.catalog.simulated.SimulatedQueue;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Business logic for the product catalog. Talks to the simulated Cosmos container
 * (products), Blob container (images) and Queue (events).
 *
 * NOTE FOR CANDIDATE: several behaviours in this class are incorrect. The test
 * suite documents the expected behaviour. Use your AI agent to locate and fix
 * the defects, then implement the bonus image-upload flow.
 */
@Service
public class ProductService {
    private final SimulatedCosmosContainer<Product> products;
    private final SimulatedBlobContainer images;
    private final SimulatedQueue events;

    public ProductService(
            SimulatedCosmosContainer<Product> products,
            SimulatedBlobContainer images,
            SimulatedQueue events) {
        this.products = products;
        this.images = images;
        this.events = events;
        seed();
    }

    /** Seeds a deterministic data set used by the pagination tests. */
    private void seed() {
        for (int i = 1; i <= 12; i++) {
            Product product = new Product(
                    "demo-" + i,
                    "Demo Product " + i,
                    "demo",
                    10 + i,
                    i,
                    null);
            products.createItem(product);
        }
    }

    public ServiceResult<List<Product>> list(String category, int page, int pageSize) {
        List<Product> all = products.query(p ->
                category == null || p.getCategory().equalsIgnoreCase(category));

        List<Product> ordered = all.stream()
                .sorted(Comparator.comparing(Product::getId))
                .toList();

        // BUG #1: pagination parameters are ignored — every matching item is
        // returned regardless of page / pageSize.
        List<Product> items = ordered;

        return ServiceResult.ok(items);
    }

    public ServiceResult<Product> get(String id) {
        Product product = products.readItem(id);

        // BUG #4: a missing product is returned as a 200 with a null body
        // instead of a 404.
        return ServiceResult.ok(product);
    }

    public ServiceResult<Product> create(CreateProductRequest request) {
        // BUG #3: no validation — a zero/negative price (or empty name) is accepted.

        Product product = new Product();
        // BUG #2: the id is never generated, so the stored document and the
        // response come back with an empty id and cannot be fetched later.
        product.setName(request.name());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());

        Product created = products.createItem(product);
        return ServiceResult.created(created);
    }

    public ServiceResult<Product> update(String id, UpdateProductRequest request) {
        Product existing = products.readItem(id);
        if (existing == null) {
            return ServiceResult.notFound("Product '" + id + "' not found");
        }

        existing.setName(request.name());
        existing.setCategory(request.category());
        existing.setPrice(request.price());
        existing.setStock(request.stock());

        Product updated = products.replaceItem(existing);
        return ServiceResult.ok(updated);
    }

    public ServiceResult<Product> delete(String id) {
        boolean removed = products.deleteItem(id);
        if (removed) {
            return ServiceResult.noContent();
        }
        return ServiceResult.notFound("Product '" + id + "' not found");
    }

    /**
     * BONUS: attach an image to a product. The expected end-to-end behaviour is:
     *   1. validate the product exists (404 otherwise),
     *   2. upload the bytes to the simulated Blob container,
     *   3. persist the resulting blob URL on the product (imageUrl),
     *   4. publish an "ImageAdded" event onto the simulated Queue.
     * This is intentionally NOT implemented — design and build it.
     */
    public ServiceResult<Product> attachImage(String id, byte[] content, String contentType) {
        throw new UnsupportedOperationException("Bonus: implement the image-upload end-to-end flow.");
    }
}