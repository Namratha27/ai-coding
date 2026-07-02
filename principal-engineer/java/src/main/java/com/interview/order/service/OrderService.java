package com.interview.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.order.model.CreateOrderRequest;
import com.interview.order.model.InventoryItem;
import com.interview.order.model.InventoryView;
import com.interview.order.model.Order;
import com.interview.order.simulated.SimulatedCosmosContainer;
import com.interview.order.simulated.SimulatedQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Order processing logic over the simulated Cosmos containers (orders,
 * inventory) and the simulated Queue (order-events).
 *
 * NOTE FOR CANDIDATE: this class contains several correctness defects around
 * idempotency, inventory reservation, cancellation/compensation and order
 * totals. The test suite specifies the intended behaviour. Use your AI agent
 * to find and fix them, then design and implement the bonus fulfillment saga.
 */
@Service
public class OrderService {
    private final SimulatedCosmosContainer<Order> orders;
    private final SimulatedCosmosContainer<InventoryItem> inventory;
    private final SimulatedQueue queue;
    private final ObjectMapper objectMapper;

    public OrderService(
            @Qualifier("ordersContainer") SimulatedCosmosContainer<Order> orders,
            @Qualifier("inventoryContainer") SimulatedCosmosContainer<InventoryItem> inventory,
            SimulatedQueue queue,
            ObjectMapper objectMapper) {
        this.orders = orders;
        this.inventory = inventory;
        this.queue = queue;
        this.objectMapper = objectMapper;
        seed();
    }

    private void seed() {
        inventory.createItem(new InventoryItem("SKU-1", 10, 0));
        inventory.createItem(new InventoryItem("SKU-2", 5, 0));
        inventory.createItem(new InventoryItem("SKU-3", 0, 0));
    }

    public ServiceResult<Order> placeOrder(String idempotencyKey, CreateOrderRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return ServiceResult.badRequest("Order must contain at least one line");
        }
        if (request.getLines().stream().anyMatch(line -> line.getQuantity() <= 0)) {
            return ServiceResult.badRequest("Line quantity must be positive");
        }

        // BUG #1: the Idempotency-Key is ignored — a repeated request creates a
        // brand new order instead of returning the previously created one.

        // BUG #2: inventory is neither checked nor updated here. Insufficient
        // stock (oversell) and unknown SKUs are accepted, and Available/Reserved
        // counts are never changed.

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus("Placed");
        order.setLines(request.getLines());
        // BUG #4: total ignores quantity (should be sum(quantity * unitPrice)).
        order.setTotal(request.getLines().stream().mapToDouble(line -> line.getUnitPrice()).sum());
        order.setCreatedAt(OffsetDateTime.now().toString());

        orders.createItem(order);
        publish("OrderPlaced", order.getId());
        return ServiceResult.created(order);
    }

    public ServiceResult<Order> getOrder(String id) {
        Order order = orders.readItem(id);
        return order == null
                ? ServiceResult.notFound("Order '" + id + "' not found")
                : ServiceResult.ok(order);
    }

    public ServiceResult<Order> cancelOrder(String id) {
        Order order = orders.readItem(id);
        if (order == null) {
            return ServiceResult.notFound("Order '" + id + "' not found");
        }

        if (Objects.equals(order.getStatus(), "Cancelled")) {
            return ServiceResult.ok(order);
        }

        // BUG #3: the order is marked cancelled but the reserved inventory is
        // never released back to Available.
        order.setStatus("Cancelled");
        orders.replaceItem(order);
        publish("OrderCancelled", order.getId());
        return ServiceResult.ok(order);
    }

    public ServiceResult<InventoryView> getInventory(String sku) {
        InventoryItem item = inventory.readItem(sku);
        return item == null
                ? ServiceResult.notFound("SKU '" + sku + "' not found")
                : ServiceResult.ok(new InventoryView(item.getSku(), item.getAvailable(), item.getReserved()));
    }

    /**
     * BONUS — event-driven fulfillment saga. The intended end-to-end flow:
     *   1. drain new "OrderPlaced" events from the queue,
     *   2. for each, load the order and transition it to "Fulfilled",
     *   3. publish an "OrderFulfilled" event,
     *   4. return how many orders were processed.
     * Design the contract across the service + backend (queue consumer) layers
     * and implement it. Intentionally not implemented.
     */
    public ServiceResult<Integer> drainAndFulfill() {
        throw new UnsupportedOperationException("Bonus: implement the OrderPlaced -> Fulfilled saga.");
    }

    private void publish(String type, String orderId) {
        try {
            queue.sendMessage(objectMapper.writeValueAsString(new Event(type, orderId)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to publish event", e);
        }
    }

    private record Event(String type, String orderId) {
    }
}
