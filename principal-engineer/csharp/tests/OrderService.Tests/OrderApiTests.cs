using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace OrderService.Tests;

/// <summary>
/// Behavioural tests for the Order Processing Service. They specify the intended
/// behaviour around idempotency, inventory reservation/oversell, cancellation
/// compensation and order totals. Several fail against the starting code.
/// Tests tagged [Trait("Category","Bonus")] cover the bonus fulfillment saga.
/// </summary>
public class OrderApiTests : IClassFixture<WebApplicationFactory<Program>>
{
    private readonly WebApplicationFactory<Program> _factory;
    public OrderApiTests(WebApplicationFactory<Program> factory) => _factory = factory;

    private HttpClient NewClient() => _factory.WithWebHostBuilder(_ => { }).CreateClient();
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private record LineDto(string sku, int quantity, decimal unitPrice);
    private record OrderResponse(string id, string status, decimal total, List<LineDto>? lines);
    private record InventoryResponse(string sku, int available, int reserved);

    private static HttpRequestMessage PostOrder(object body, string? idempotencyKey = null)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, "/orders")
        {
            Content = JsonContent.Create(body)
        };
        if (idempotencyKey is not null) req.Headers.Add("Idempotency-Key", idempotencyKey);
        return req;
    }

    private static object Order(params object[] lines) => new { lines };
    private static object Line(string sku, int qty, decimal price) => new { sku, quantity = qty, unitPrice = price };

    [Fact]
    public async Task Health_Returns_Ok()
    {
        var res = await NewClient().GetAsync("/health");
        Assert.Equal(HttpStatusCode.OK, res.StatusCode);
    }

    [Fact] // regression — should already pass
    public async Task Create_Order_Returns_201_Placed_With_Id()
    {
        var client = NewClient();
        var res = await client.SendAsync(PostOrder(Order(Line("SKU-1", 1, 10m))));
        Assert.Equal(HttpStatusCode.Created, res.StatusCode);
        var order = await res.Content.ReadFromJsonAsync<OrderResponse>(Json);
        Assert.NotNull(order);
        Assert.False(string.IsNullOrWhiteSpace(order!.id));
        Assert.Equal("Placed", order.status);
    }

    [Fact] // Defect #4 — total ignores quantity
    public async Task Order_Total_Uses_Quantity()
    {
        var client = NewClient();
        var res = await client.SendAsync(PostOrder(Order(Line("SKU-1", 2, 10m), Line("SKU-2", 3, 5m))));
        res.EnsureSuccessStatusCode();
        var order = await res.Content.ReadFromJsonAsync<OrderResponse>(Json);
        Assert.Equal(35m, order!.total); // 2*10 + 3*5
    }

    [Fact] // Defect #2 — reservation not applied
    public async Task Valid_Order_Reserves_Inventory()
    {
        var client = NewClient();
        var res = await client.SendAsync(PostOrder(Order(Line("SKU-1", 3, 10m))));
        res.EnsureSuccessStatusCode();

        var inv = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(7, inv!.available);
        Assert.Equal(3, inv.reserved);
    }

    [Fact] // Defect #2 — oversell must be rejected
    public async Task Oversell_Is_Rejected_And_Inventory_Unchanged()
    {
        var client = NewClient();
        var res = await client.SendAsync(PostOrder(Order(Line("SKU-1", 99, 10m))));
        Assert.Equal(HttpStatusCode.Conflict, res.StatusCode);

        var inv = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(10, inv!.available);
        Assert.Equal(0, inv.reserved);
    }

    [Fact] // Defect #2 — unknown sku rejected
    public async Task Unknown_Sku_Is_Rejected()
    {
        var client = NewClient();
        var res = await client.SendAsync(PostOrder(Order(Line("SKU-X", 1, 10m))));
        Assert.Equal(HttpStatusCode.Conflict, res.StatusCode);
    }

    [Fact] // Defect #1 — idempotency
    public async Task Repeated_IdempotencyKey_Returns_Same_Order_And_Reserves_Once()
    {
        var client = NewClient();
        var first = await client.SendAsync(PostOrder(Order(Line("SKU-1", 2, 10m)), "key-1"));
        var second = await client.SendAsync(PostOrder(Order(Line("SKU-1", 2, 10m)), "key-1"));

        var o1 = await first.Content.ReadFromJsonAsync<OrderResponse>(Json);
        var o2 = await second.Content.ReadFromJsonAsync<OrderResponse>(Json);
        Assert.Equal(o1!.id, o2!.id);

        var inv = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(8, inv!.available); // reserved exactly once
    }

    [Fact]
    public async Task Get_Missing_Order_Returns_404()
    {
        var res = await NewClient().GetAsync("/orders/nope");
        Assert.Equal(HttpStatusCode.NotFound, res.StatusCode);
    }

    [Fact] // Defect #3 — cancel restores inventory
    public async Task Cancel_Restores_Inventory()
    {
        var client = NewClient();
        var place = await client.SendAsync(PostOrder(Order(Line("SKU-1", 4, 10m))));
        var order = await place.Content.ReadFromJsonAsync<OrderResponse>(Json);

        // Placing must reserve stock first (10 - 4 = 6).
        var afterPlace = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(6, afterPlace!.available);

        var cancel = await client.PostAsync($"/orders/{order!.id}/cancel", null);
        Assert.Equal(HttpStatusCode.OK, cancel.StatusCode);
        var cancelled = await cancel.Content.ReadFromJsonAsync<OrderResponse>(Json);
        Assert.Equal("Cancelled", cancelled!.status);

        var inv = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(10, inv!.available);
        Assert.Equal(0, inv.reserved);
    }

    [Fact] // Defect #3 — cancel is idempotent (no double restore)
    public async Task Cancel_Twice_Does_Not_Double_Restore()
    {
        var client = NewClient();
        var place = await client.SendAsync(PostOrder(Order(Line("SKU-1", 4, 10m))));
        var order = await place.Content.ReadFromJsonAsync<OrderResponse>(Json);

        // Placing must reserve stock first (10 - 4 = 6).
        var afterPlace = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(6, afterPlace!.available);

        await client.PostAsync($"/orders/{order!.id}/cancel", null);
        await client.PostAsync($"/orders/{order.id}/cancel", null);

        var inv = await client.GetFromJsonAsync<InventoryResponse>("/inventory/SKU-1", Json);
        Assert.Equal(10, inv!.available); // not 14
    }

    [Fact]
    [Trait("Category", "Bonus")]
    public async Task Bonus_Drain_Events_Fulfills_Order()
    {
        var client = NewClient();
        var place = await client.SendAsync(PostOrder(Order(Line("SKU-1", 1, 10m))));
        var order = await place.Content.ReadFromJsonAsync<OrderResponse>(Json);

        var drain = await client.PostAsync("/internal/drain-events", null);
        Assert.Equal(HttpStatusCode.OK, drain.StatusCode);

        var fetched = await client.GetFromJsonAsync<OrderResponse>($"/orders/{order!.id}", Json);
        Assert.Equal("Fulfilled", fetched!.status);
    }

    [Fact]
    [Trait("Category", "Bonus")]
    public async Task Bonus_Drain_Events_Publishes_OrderFulfilled()
    {
        var client = NewClient();
        var place = await client.SendAsync(PostOrder(Order(Line("SKU-2", 1, 5m))));
        var order = await place.Content.ReadFromJsonAsync<OrderResponse>(Json);

        await client.PostAsync("/internal/drain-events", null);

        var events = await client.GetFromJsonAsync<List<string>>("/internal/events", Json);
        Assert.NotNull(events);
        Assert.Contains(events!, e => e.Contains("OrderFulfilled"));
    }
}
