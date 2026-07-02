using CatalogService.Models;
using CatalogService.Simulated;

namespace CatalogService.Services;

/// <summary>
/// Business logic for the product catalog. Talks to the simulated Cosmos
/// container (products), Blob container (images) and Queue (events).
///
/// NOTE FOR CANDIDATE: several behaviours in this class are incorrect. The test
/// suite documents the expected behaviour. Use your AI agent to locate and fix
/// the defects, then implement the bonus image-upload flow.
/// </summary>
public class ProductService
{
    private readonly SimulatedCosmosContainer<Product> _products;
    private readonly SimulatedBlobContainer _images;
    private readonly SimulatedQueue _events;

    public ProductService(
        SimulatedCosmosContainer<Product> products,
        SimulatedBlobContainer images,
        SimulatedQueue events)
    {
        _products = products;
        _images = images;
        _events = events;
        Seed();
    }

    /// <summary>Seeds a deterministic data set used by the pagination tests.</summary>
    private void Seed()
    {
        for (var i = 1; i <= 12; i++)
        {
            var p = new Product
            {
                Id = $"demo-{i}",
                Name = $"Demo Product {i}",
                Category = "demo",
                Price = 10m + i,
                Stock = i
            };
            _products.CreateItemAsync(p);
        }
    }

    public async Task<ServiceResult<IReadOnlyList<Product>>> ListAsync(string? category, int page, int pageSize)
    {
        var all = await _products.QueryAsync(p =>
            category is null || p.Category.Equals(category, StringComparison.OrdinalIgnoreCase));

        var ordered = all.OrderBy(p => p.Id, StringComparer.Ordinal).ToList();

        // BUG #1: pagination parameters are ignored — every matching item is
        // returned regardless of page / pageSize.
        IReadOnlyList<Product> items = ordered;

        return ServiceResult<IReadOnlyList<Product>>.Ok(items);
    }

    public async Task<ServiceResult<Product>> GetAsync(string id)
    {
        var product = await _products.ReadItemAsync(id);

        // BUG #4: a missing product is returned as a 200 with a null body
        // instead of a 404.
        return ServiceResult<Product>.Ok(product!);
    }

    public async Task<ServiceResult<Product>> CreateAsync(CreateProductRequest request)
    {
        // BUG #3: no validation — a zero/negative price (or empty name) is accepted.

        var product = new Product
        {
            // BUG #2: the id is never generated, so the stored document and the
            // response come back with an empty id and cannot be fetched later.
            Name = request.Name,
            Category = request.Category,
            Price = request.Price,
            Stock = request.Stock
        };

        var created = await _products.CreateItemAsync(product);
        return ServiceResult<Product>.Created(created);
    }

    public async Task<ServiceResult<Product>> UpdateAsync(string id, UpdateProductRequest request)
    {
        var existing = await _products.ReadItemAsync(id);
        if (existing is null)
        {
            return ServiceResult<Product>.NotFound($"Product '{id}' not found");
        }

        existing.Name = request.Name;
        existing.Category = request.Category;
        existing.Price = request.Price;
        existing.Stock = request.Stock;

        var updated = await _products.ReplaceItemAsync(existing);
        return ServiceResult<Product>.Ok(updated);
    }

    public async Task<ServiceResult<Product>> DeleteAsync(string id)
    {
        var removed = await _products.DeleteItemAsync(id);
        return removed
            ? ServiceResult<Product>.NoContent()
            : ServiceResult<Product>.NotFound($"Product '{id}' not found");
    }

    /// <summary>
    /// BONUS: attach an image to a product. The expected end-to-end behaviour is:
    ///   1. validate the product exists (404 otherwise),
    ///   2. upload the bytes to the simulated Blob container,
    ///   3. persist the resulting blob URL on the product (ImageUrl),
    ///   4. publish an "ImageAdded" event onto the simulated Queue.
    /// This is intentionally NOT implemented — design and build it.
    /// </summary>
    public Task<ServiceResult<Product>> AttachImageAsync(string id, byte[] content, string contentType)
    {
        throw new NotImplementedException("Bonus: implement the image-upload end-to-end flow.");
    }
}
