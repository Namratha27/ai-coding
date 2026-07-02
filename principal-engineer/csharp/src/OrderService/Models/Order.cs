using OrderService.Simulated;

namespace OrderService.Models;

public class InventoryItem : ICosmosItem
{
    // For inventory, the document id is the SKU.
    public string Id { get; set; } = string.Empty;
    public string Sku { get => Id; set => Id = value; }
    public int Available { get; set; }
    public int Reserved { get; set; }
}

public class OrderLine
{
    public string Sku { get; set; } = string.Empty;
    public int Quantity { get; set; }
    public decimal UnitPrice { get; set; }
}

public class Order : ICosmosItem
{
    public string Id { get; set; } = string.Empty;
    public string? IdempotencyKey { get; set; }
    public string Status { get; set; } = "Placed";
    public List<OrderLine> Lines { get; set; } = new();
    public decimal Total { get; set; }
    public string CreatedAt { get; set; } = string.Empty;
}

public record CreateOrderRequest(List<OrderLine> Lines);

public record InventoryView(string Sku, int Available, int Reserved);
