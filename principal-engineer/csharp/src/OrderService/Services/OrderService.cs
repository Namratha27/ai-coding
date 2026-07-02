using System.Text.Json;
using OrderService.Models;
using OrderService.Simulated;

namespace OrderService.Services;

/// <summary>
/// Order processing logic over the simulated Cosmos containers (orders,
/// inventory) and the simulated Queue (order-events).
///
/// NOTE FOR CANDIDATE: this class contains several correctness defects around
/// idempotency, inventory reservation, cancellation/compensation and order
/// totals. The test suite specifies the intended behaviour. Use your AI agent
/// to find and fix them, then design and implement the bonus fulfillment saga.
/// </summary>
public class OrderService
{
    private readonly SimulatedCosmosContainer<Order> _orders;
    private readonly SimulatedCosmosContainer<InventoryItem> _inventory;
    private readonly SimulatedQueue _queue;

    public OrderService(
        SimulatedCosmosContainer<Order> orders,
        SimulatedCosmosContainer<InventoryItem> inventory,
        SimulatedQueue queue)
    {
        _orders = orders;
        _inventory = inventory;
        _queue = queue;
        Seed();
    }

    private void Seed()
    {
        _inventory.CreateItemAsync(new InventoryItem { Sku = "SKU-1", Available = 10, Reserved = 0 });
        _inventory.CreateItemAsync(new InventoryItem { Sku = "SKU-2", Available = 5, Reserved = 0 });
        _inventory.CreateItemAsync(new InventoryItem { Sku = "SKU-3", Available = 0, Reserved = 0 });
    }

    public async Task<ServiceResult<Order>> PlaceOrderAsync(string? idempotencyKey, CreateOrderRequest request)
    {
        if (request.Lines is null || request.Lines.Count == 0)
            return ServiceResult<Order>.BadRequest("Order must contain at least one line");
        if (request.Lines.Any(l => l.Quantity <= 0))
            return ServiceResult<Order>.BadRequest("Line quantity must be positive");

        // BUG #1: the Idempotency-Key is ignored — a repeated request creates a
        // brand new order instead of returning the previously created one.

        // BUG #2: inventory is neither checked nor updated here. Insufficient
        // stock (oversell) and unknown SKUs are accepted, and Available/Reserved
        // counts are never changed.

        var order = new Order
        {
            Id = Guid.NewGuid().ToString(),
            IdempotencyKey = idempotencyKey,
            Status = "Placed",
            Lines = request.Lines,
            // BUG #4: total ignores quantity (should be sum(quantity * unitPrice)).
            Total = request.Lines.Sum(l => l.UnitPrice),
            CreatedAt = DateTimeOffset.UtcNow.ToString("o")
        };

        await _orders.CreateItemAsync(order);
        await PublishAsync("OrderPlaced", order.Id);
        return ServiceResult<Order>.Created(order);
    }

    public async Task<ServiceResult<Order>> GetOrderAsync(string id)
    {
        var order = await _orders.ReadItemAsync(id);
        return order is null
            ? ServiceResult<Order>.NotFound($"Order '{id}' not found")
            : ServiceResult<Order>.Ok(order);
    }

    public async Task<ServiceResult<Order>> CancelOrderAsync(string id)
    {
        var order = await _orders.ReadItemAsync(id);
        if (order is null)
            return ServiceResult<Order>.NotFound($"Order '{id}' not found");

        if (order.Status == "Cancelled")
            return ServiceResult<Order>.Ok(order); // already cancelled — idempotent

        // BUG #3: the order is marked cancelled but the reserved inventory is
        // never released back to Available.
        order.Status = "Cancelled";
        await _orders.ReplaceItemAsync(order);
        await PublishAsync("OrderCancelled", order.Id);
        return ServiceResult<Order>.Ok(order);
    }

    public async Task<ServiceResult<InventoryView>> GetInventoryAsync(string sku)
    {
        var item = await _inventory.ReadItemAsync(sku);
        return item is null
            ? ServiceResult<InventoryView>.NotFound($"SKU '{sku}' not found")
            : ServiceResult<InventoryView>.Ok(new InventoryView(item.Sku, item.Available, item.Reserved));
    }

    /// <summary>
    /// BONUS — event-driven fulfillment saga. The intended end-to-end flow:
    ///   1. drain new "OrderPlaced" events from the queue,
    ///   2. for each, load the order and transition it to "Fulfilled",
    ///   3. publish an "OrderFulfilled" event,
    ///   4. return how many orders were processed.
    /// Design the contract across the service + backend (queue consumer) layers
    /// and implement it. Intentionally not implemented.
    /// </summary>
    public Task<ServiceResult<int>> DrainAndFulfillAsync()
    {
        throw new NotImplementedException("Bonus: implement the OrderPlaced -> Fulfilled saga.");
    }

    private Task PublishAsync(string type, string orderId) =>
        _queue.SendMessageAsync(JsonSerializer.Serialize(new { type, orderId }));
}
