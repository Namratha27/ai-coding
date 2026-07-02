using OrderService.Models;
using OrderService.Services;
using OrderService.Simulated;

var builder = WebApplication.CreateBuilder(args);

// --- Simulated Azure dependencies (in-memory, no emulator required) ----------
builder.Services.AddSingleton<SimulatedCosmosContainer<Order>>();
builder.Services.AddSingleton<SimulatedCosmosContainer<InventoryItem>>();
builder.Services.AddSingleton<SimulatedQueue>();
builder.Services.AddSingleton<OrderService.Services.OrderService>();

var app = builder.Build();

static IResult ToHttp<T>(ServiceResult<T> r) => r.Status switch
{
    200 => Results.Ok(r.Value),
    201 => Results.Created($"/orders/{(r.Value as Order)?.Id}", r.Value),
    204 => Results.NoContent(),
    400 => Results.BadRequest(new { error = r.Error }),
    404 => Results.NotFound(new { error = r.Error }),
    409 => Results.Conflict(new { error = r.Error }),
    _ => Results.StatusCode(r.Status)
};

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.MapPost("/orders", async (CreateOrderRequest request, HttpRequest http, OrderService.Services.OrderService svc) =>
{
    var key = http.Headers.TryGetValue("Idempotency-Key", out var v) ? v.ToString() : null;
    var result = await svc.PlaceOrderAsync(string.IsNullOrWhiteSpace(key) ? null : key, request);
    return ToHttp(result);
});

app.MapGet("/orders/{id}", async (string id, OrderService.Services.OrderService svc) =>
    ToHttp(await svc.GetOrderAsync(id)));

app.MapPost("/orders/{id}/cancel", async (string id, OrderService.Services.OrderService svc) =>
    ToHttp(await svc.CancelOrderAsync(id)));

app.MapGet("/inventory/{sku}", async (string sku, OrderService.Services.OrderService svc) =>
    ToHttp(await svc.GetInventoryAsync(sku)));

// BONUS endpoint — drains the queue and fulfills placed orders (not implemented).
app.MapPost("/internal/drain-events", async (OrderService.Services.OrderService svc) =>
{
    var result = await svc.DrainAndFulfillAsync();
    return result.Status == 200
        ? Results.Ok(new { processed = result.Value })
        : ToHttp(result);
});

// Observability into the simulated queue (useful locally and for tests).
app.MapGet("/internal/events", (SimulatedQueue queue) => Results.Ok(queue.Messages));

app.Run();

// Exposed so the test project can host the app with WebApplicationFactory.
public partial class Program { }
