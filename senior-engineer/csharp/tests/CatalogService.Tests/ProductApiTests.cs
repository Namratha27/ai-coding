using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using CatalogService.Models;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace CatalogService.Tests;

/// <summary>
/// Behavioural tests for the Product Catalog Service. They describe how the API
/// is SUPPOSED to behave. Several fail against the starting code because of the
/// seeded defects; making them pass (without weakening them) is the task.
/// Tests tagged [Trait("Category","Bonus")] cover the bonus image-upload flow.
/// </summary>
public class ProductApiTests : IClassFixture<WebApplicationFactory<Program>>
{
    private readonly WebApplicationFactory<Program> _factory;

    public ProductApiTests(WebApplicationFactory<Program> factory) => _factory = factory;

    // Each test gets its own app instance => fresh, re-seeded in-memory stores.
    private HttpClient NewClient() => _factory.WithWebHostBuilder(_ => { }).CreateClient();

    private static readonly JsonSerializerOptions Json =
        new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task Health_Returns_Ok()
    {
        var client = NewClient();
        var res = await client.GetAsync("/health");
        Assert.Equal(HttpStatusCode.OK, res.StatusCode);
    }

    [Fact] // Defect #1 — pagination
    public async Task List_Respects_PageSize()
    {
        var client = NewClient();
        var res = await client.GetAsync("/products?category=demo&page=1&pageSize=5");
        res.EnsureSuccessStatusCode();
        var items = await res.Content.ReadFromJsonAsync<List<Product>>(Json);
        Assert.NotNull(items);
        Assert.Equal(5, items!.Count);
    }

    [Fact] // Defect #1 — pagination, last page
    public async Task List_Returns_Remainder_On_Last_Page()
    {
        var client = NewClient();
        // 12 seeded "demo" products, pageSize 5 => page 3 has 2 items.
        var res = await client.GetAsync("/products?category=demo&page=3&pageSize=5");
        res.EnsureSuccessStatusCode();
        var items = await res.Content.ReadFromJsonAsync<List<Product>>(Json);
        Assert.NotNull(items);
        Assert.Equal(2, items!.Count);
    }

    [Fact] // Defect #2 — id generation
    public async Task Create_Assigns_NonEmpty_Id_And_Is_Retrievable()
    {
        var client = NewClient();
        var create = await client.PostAsJsonAsync("/products",
            new CreateProductRequest("Keyboard", "peripherals", 49.99m, 7));
        Assert.Equal(HttpStatusCode.Created, create.StatusCode);

        var created = await create.Content.ReadFromJsonAsync<Product>(Json);
        Assert.NotNull(created);
        Assert.False(string.IsNullOrWhiteSpace(created!.Id));

        var get = await client.GetAsync($"/products/{created.Id}");
        Assert.Equal(HttpStatusCode.OK, get.StatusCode);
        var fetched = await get.Content.ReadFromJsonAsync<Product>(Json);
        Assert.Equal("Keyboard", fetched!.Name);
    }

    [Fact] // Defect #3 — validation
    public async Task Create_Rejects_NonPositive_Price()
    {
        var client = NewClient();
        var res = await client.PostAsJsonAsync("/products",
            new CreateProductRequest("Freebie", "demo", 0m, 1));
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact] // Defect #3 — validation
    public async Task Create_Rejects_Negative_Price()
    {
        var client = NewClient();
        var res = await client.PostAsJsonAsync("/products",
            new CreateProductRequest("Negative", "demo", -5m, 1));
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact] // Defect #4 — 404 semantics
    public async Task Get_Missing_Returns_404()
    {
        var client = NewClient();
        var res = await client.GetAsync("/products/does-not-exist");
        Assert.Equal(HttpStatusCode.NotFound, res.StatusCode);
    }

    [Fact] // Should already pass — guards against regressions
    public async Task Delete_Then_Get_Returns_404()
    {
        var client = NewClient();
        var del = await client.DeleteAsync("/products/demo-1");
        Assert.Equal(HttpStatusCode.NoContent, del.StatusCode);

        var get = await client.GetAsync("/products/demo-1");
        Assert.Equal(HttpStatusCode.NotFound, get.StatusCode);
    }

    [Fact] // Should already pass — category filter
    public async Task List_Filters_By_Category()
    {
        var client = NewClient();
        var res = await client.GetAsync("/products?category=nonexistent&page=1&pageSize=10");
        res.EnsureSuccessStatusCode();
        var items = await res.Content.ReadFromJsonAsync<List<Product>>(Json);
        Assert.NotNull(items);
        Assert.Empty(items!);
    }

    [Fact]
    [Trait("Category", "Bonus")]
    public async Task Bonus_Upload_Image_Sets_Url_And_Publishes_Event()
    {
        var client = NewClient();

        var content = new ByteArrayContent(new byte[] { 1, 2, 3, 4 });
        content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("image/png");
        var upload = await client.PostAsync("/products/demo-2/image", content);
        Assert.Equal(HttpStatusCode.OK, upload.StatusCode);

        var product = await upload.Content.ReadFromJsonAsync<Product>(Json);
        Assert.NotNull(product);
        Assert.False(string.IsNullOrWhiteSpace(product!.ImageUrl));

        // The image URL must be persisted on the product.
        var get = await client.GetAsync("/products/demo-2");
        var fetched = await get.Content.ReadFromJsonAsync<Product>(Json);
        Assert.Equal(product.ImageUrl, fetched!.ImageUrl);
    }

    [Fact]
    [Trait("Category", "Bonus")]
    public async Task Bonus_Upload_Image_For_Missing_Product_Returns_404()
    {
        var client = NewClient();
        var content = new ByteArrayContent(new byte[] { 9 });
        content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("image/png");
        var upload = await client.PostAsync("/products/missing/image", content);
        Assert.Equal(HttpStatusCode.NotFound, upload.StatusCode);
    }
}
