using CatalogService.Models;
using CatalogService.Services;
using CatalogService.Simulated;

var builder = WebApplication.CreateBuilder(args);

// --- Simulated Azure dependencies (in-memory, no emulator required) ----------
builder.Services.AddSingleton<SimulatedCosmosContainer<Product>>();
builder.Services.AddSingleton<SimulatedBlobContainer>();
builder.Services.AddSingleton<SimulatedQueue>();
builder.Services.AddSingleton<ProductService>();

var app = builder.Build();

static IResult ToHttp<T>(ServiceResult<T> r) => r.Status switch
{
    200 => Results.Ok(r.Value),
    201 => Results.Created($"/products/{(r.Value as Product)?.Id}", r.Value),
    204 => Results.NoContent(),
    400 => Results.BadRequest(new { error = r.Error }),
    404 => Results.NotFound(new { error = r.Error }),
    _ => Results.StatusCode(r.Status)
};

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.MapGet("/products", async (string? category, int? page, int? pageSize, ProductService svc) =>
{
    var result = await svc.ListAsync(category, page ?? 1, pageSize ?? 10);
    return ToHttp(result);
});

app.MapGet("/products/{id}", async (string id, ProductService svc) =>
{
    var result = await svc.GetAsync(id);
    return ToHttp(result);
});

app.MapPost("/products", async (CreateProductRequest request, ProductService svc) =>
{
    var result = await svc.CreateAsync(request);
    return ToHttp(result);
});

app.MapPut("/products/{id}", async (string id, UpdateProductRequest request, ProductService svc) =>
{
    var result = await svc.UpdateAsync(id, request);
    return ToHttp(result);
});

app.MapDelete("/products/{id}", async (string id, ProductService svc) =>
{
    var result = await svc.DeleteAsync(id);
    return ToHttp(result);
});

// BONUS endpoint — wired up but the service method is not implemented yet.
app.MapPost("/products/{id}/image", async (string id, HttpRequest req, ProductService svc) =>
{
    using var ms = new MemoryStream();
    await req.Body.CopyToAsync(ms);
    var result = await svc.AttachImageAsync(id, ms.ToArray(), req.ContentType ?? "application/octet-stream");
    return ToHttp(result);
});

app.Run();

// Exposed so the test project can spin up the app with WebApplicationFactory.
public partial class Program { }
