using CatalogService.Simulated;

namespace CatalogService.Models;

public class Product : ICosmosItem
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Category { get; set; } = string.Empty;
    public decimal Price { get; set; }
    public int Stock { get; set; }
    public string? ImageUrl { get; set; }
}

/// <summary>Request body for creating a product.</summary>
public record CreateProductRequest(string Name, string Category, decimal Price, int Stock);

/// <summary>Request body for updating a product.</summary>
public record UpdateProductRequest(string Name, string Category, decimal Price, int Stock);
