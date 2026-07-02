namespace CatalogService.Simulated;

/// <summary>
/// Marker for items that can be stored in the simulated Cosmos DB container.
/// Mirrors the requirement that every Cosmos document has a string "id".
/// </summary>
public interface ICosmosItem
{
    string Id { get; set; }
}
