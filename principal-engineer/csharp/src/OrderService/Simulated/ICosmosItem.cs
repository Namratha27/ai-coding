namespace OrderService.Simulated;

/// <summary>Marker for items stored in the simulated Cosmos DB container.</summary>
public interface ICosmosItem
{
    string Id { get; set; }
}
