using System.Collections.Concurrent;

namespace OrderService.Simulated;

/// <summary>
/// In-memory stand-in for an Azure Cosmos DB container (no emulator/network).
/// Method names mirror the real Cosmos SDK. One instance models one container;
/// the app uses one for "orders" and one for "inventory".
/// </summary>
public class SimulatedCosmosContainer<T> where T : class, ICosmosItem
{
    private readonly ConcurrentDictionary<string, T> _store = new();

    public Task<T> CreateItemAsync(T item)
    {
        _store[item.Id] = item;
        return Task.FromResult(item);
    }

    public Task<T?> ReadItemAsync(string id)
    {
        _store.TryGetValue(id, out var item);
        return Task.FromResult<T?>(item);
    }

    public Task<IReadOnlyList<T>> QueryAsync(Func<T, bool> predicate)
    {
        IReadOnlyList<T> results = _store.Values.Where(predicate).ToList();
        return Task.FromResult(results);
    }

    public Task<T> ReplaceItemAsync(T item)
    {
        _store[item.Id] = item;
        return Task.FromResult(item);
    }

    public Task<bool> DeleteItemAsync(string id) => Task.FromResult(_store.TryRemove(id, out _));
}
