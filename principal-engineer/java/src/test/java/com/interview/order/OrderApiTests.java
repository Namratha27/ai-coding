package com.interview.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural tests for the Order Processing Service. They specify the intended
 * behaviour around idempotency, inventory reservation/oversell, cancellation
 * compensation and order totals. Several fail against the starting code.
 * Tests tagged "Bonus" cover the bonus fulfillment saga.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderApiTests {
    @Autowired
    private TestRestTemplate client;

    @Test
    void healthReturnsOk() {
        ResponseEntity<JsonNode> response = client.getForEntity("/health", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createOrderReturns201PlacedWithId() {
        ResponseEntity<JsonNode> response = postOrder(order(line("SKU-1", 1, 10.0)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("id").asText()).isNotBlank();
        assertThat(response.getBody().get("status").asText()).isEqualTo("Placed");
    }

    @Test
    void orderTotalUsesQuantity() {
        ResponseEntity<JsonNode> response = postOrder(order(line("SKU-1", 2, 10.0), line("SKU-2", 3, 5.0)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("total").asDouble()).isEqualTo(35.0);
    }

    @Test
    void validOrderReservesInventory() {
        ResponseEntity<JsonNode> response = postOrder(order(line("SKU-1", 3, 10.0)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode inventory = getJson("/inventory/SKU-1");
        assertThat(inventory.get("available").asInt()).isEqualTo(7);
        assertThat(inventory.get("reserved").asInt()).isEqualTo(3);
    }

    @Test
    void oversellIsRejectedAndInventoryUnchanged() {
        ResponseEntity<JsonNode> response = postOrder(order(line("SKU-1", 99, 10.0)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode inventory = getJson("/inventory/SKU-1");
        assertThat(inventory.get("available").asInt()).isEqualTo(10);
        assertThat(inventory.get("reserved").asInt()).isEqualTo(0);
    }

    @Test
    void unknownSkuIsRejected() {
        ResponseEntity<JsonNode> response = postOrder(order(line("SKU-X", 1, 10.0)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void repeatedIdempotencyKeyReturnsSameOrderAndReservesOnce() {
        String payload = order(line("SKU-1", 2, 10.0));

        ResponseEntity<JsonNode> first = postOrder(payload, "key-1");
        ResponseEntity<JsonNode> second = postOrder(payload, "key-1");

        assertThat(first.getBody().get("id").asText()).isEqualTo(second.getBody().get("id").asText());

        JsonNode inventory = getJson("/inventory/SKU-1");
        assertThat(inventory.get("available").asInt()).isEqualTo(8);
        assertThat(inventory.get("reserved").asInt()).isEqualTo(2);
    }

    @Test
    void getMissingOrderReturns404() {
        ResponseEntity<JsonNode> response = client.getForEntity("/orders/nope", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelRestoresInventory() {
        ResponseEntity<JsonNode> place = postOrder(order(line("SKU-1", 4, 10.0)));
        assertThat(place.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Placing must reserve stock first (10 - 4 = 6).
        JsonNode afterPlace = getJson("/inventory/SKU-1");
        assertThat(afterPlace.get("available").asInt()).isEqualTo(6);

        ResponseEntity<JsonNode> cancel = client.postForEntity(
                "/orders/" + place.getBody().get("id").asText() + "/cancel", null, JsonNode.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancel.getBody().get("status").asText()).isEqualTo("Cancelled");

        JsonNode inventory = getJson("/inventory/SKU-1");
        assertThat(inventory.get("available").asInt()).isEqualTo(10);
        assertThat(inventory.get("reserved").asInt()).isEqualTo(0);
    }

    @Test
    void cancelTwiceDoesNotDoubleRestore() {
        ResponseEntity<JsonNode> place = postOrder(order(line("SKU-1", 4, 10.0)));
        assertThat(place.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Placing must reserve stock first (10 - 4 = 6).
        JsonNode afterPlace = getJson("/inventory/SKU-1");
        assertThat(afterPlace.get("available").asInt()).isEqualTo(6);

        String cancelPath = "/orders/" + place.getBody().get("id").asText() + "/cancel";
        assertThat(client.postForEntity(cancelPath, null, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.postForEntity(cancelPath, null, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode inventory = getJson("/inventory/SKU-1");
        assertThat(inventory.get("available").asInt()).isEqualTo(10);
        assertThat(inventory.get("reserved").asInt()).isEqualTo(0);
    }

    @Test
    void bonusDrainEventsFulfillsOrder() {
        ResponseEntity<JsonNode> place = postOrder(order(line("SKU-1", 1, 10.0)));
        assertThat(place.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> drain = client.postForEntity("/internal/drain-events", null, JsonNode.class);
        assertThat(drain.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode fetched = getJson("/orders/" + place.getBody().get("id").asText());
        assertThat(fetched.get("status").asText()).isEqualTo("Fulfilled");
    }

    @Test
    void bonusDrainEventsPublishesOrderFulfilled() {
        ResponseEntity<JsonNode> place = postOrder(order(line("SKU-2", 1, 5.0)));
        assertThat(place.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        client.postForEntity("/internal/drain-events", null, JsonNode.class);

        ResponseEntity<List<String>> eventsResponse = client.exchange(
                "/internal/events", HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventsResponse.getBody()).anyMatch(event -> event.contains("OrderFulfilled"));
    }

    private ResponseEntity<JsonNode> postOrder(String body) {
        return postOrder(body, null);
    }

    private ResponseEntity<JsonNode> postOrder(String body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.add("Idempotency-Key", idempotencyKey);
        }
        return client.postForEntity("/orders", new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode getJson(String path) {
        ResponseEntity<JsonNode> response = client.getForEntity(path, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static String order(String... lines) {
        return "{\"lines\":[" + String.join(",", lines) + "]}";
    }

    private static String line(String sku, int quantity, double unitPrice) {
        return "{\"sku\":\"" + sku + "\",\"quantity\":" + quantity + ",\"unitPrice\":" + unitPrice + "}";
    }
}
