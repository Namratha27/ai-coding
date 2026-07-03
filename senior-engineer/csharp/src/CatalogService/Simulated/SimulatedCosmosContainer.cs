using System.Collections.Concurrent;

namespace CatalogService.Simulated;

/// <summary>
/// In-memory stand-in for an Azure Cosmos DB container. No network, no emulator.
/// Methods are intentionally named to resemble the real Cosmos SDK so the shape
/// of the problem is realistic. Data lives only for the lifetime of the process.
/// </summary>
public class SimulatedCosmosContainer<T> where T : class, ICosmosItem
{
    private readonly ConcurrentDictionary<string, T> _store = new();

    public Task<T> CreateItemAsync(T item)
    {
        // Last-writer-wins on the document id, like an upsert keyed by id.
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

    public Task<bool> DeleteItemAsync(string id)
    {
        return Task.FromResult(_store.TryRemove(id, out _));
    }
}
