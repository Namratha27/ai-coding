package com.interview.catalog;

import com.interview.catalog.model.CreateProductRequest;
import com.interview.catalog.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural tests for the Product Catalog Service. They describe how the API
 * is SUPPOSED to behave. Several fail against the starting code because of the
 * seeded defects; making them pass (without weakening them) is the task.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductApiTests {
    @Autowired
    private TestRestTemplate client;

    @Test
    void healthReturnsOk() {
        ResponseEntity<Map> response = client.getForEntity("/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
    }

    @Test
    void listRespectsPageSize() {
        ResponseEntity<Product[]> response = client.getForEntity(
                "/products?category=demo&page=1&pageSize=5", Product[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(5);
    }

    @Test
    void listReturnsRemainderOnLastPage() {
        ResponseEntity<Product[]> response = client.getForEntity(
                "/products?category=demo&page=3&pageSize=5", Product[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
    }

    @Test
    void createAssignsNonEmptyIdAndIsRetrievable() {
        ResponseEntity<Product> create = client.postForEntity(
                "/products",
                new CreateProductRequest("Keyboard", "peripherals", 49.99, 7),
                Product.class);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Product created = create.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotBlank();

        ResponseEntity<Product> get = client.getForEntity("/products/" + created.getId(), Product.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody().getName()).isEqualTo("Keyboard");
    }

    @Test
    void createRejectsZeroPrice() {
        ResponseEntity<String> response = client.postForEntity(
                "/products",
                new CreateProductRequest("Freebie", "demo", 0, 1),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsNegativePrice() {
        ResponseEntity<String> response = client.postForEntity(
                "/products",
                new CreateProductRequest("Negative", "demo", -5, 1),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getMissingReturns404() {
        ResponseEntity<String> response = client.getForEntity("/products/does-not-exist", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteThenGetReturns404() {
        ResponseEntity<Void> delete = client.exchange("/products/demo-1", HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> get = client.getForEntity("/products/demo-1", String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listFiltersByCategory() {
        ResponseEntity<Product[]> response = client.getForEntity(
                "/products?category=nonexistent&page=1&pageSize=10", Product[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    void bonusUploadImageSetsUrlAndPublishesEvent() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        HttpEntity<byte[]> request = new HttpEntity<>(new byte[] {1, 2, 3, 4}, headers);

        ResponseEntity<Product> upload = client.postForEntity("/products/demo-2/image", request, Product.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);

        Product product = upload.getBody();
        assertThat(product).isNotNull();
        assertThat(product.getImageUrl()).isNotBlank();

        ResponseEntity<Product> get = client.getForEntity("/products/demo-2", Product.class);
        assertThat(get.getBody().getImageUrl()).isEqualTo(product.getImageUrl());
    }

    @Test
    void bonusUploadImageForMissingProductReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        HttpEntity<byte[]> request = new HttpEntity<>(new byte[] {9}, headers);

        ResponseEntity<String> upload = client.postForEntity("/products/missing/image", request, String.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}