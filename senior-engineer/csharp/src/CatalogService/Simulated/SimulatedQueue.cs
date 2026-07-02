using System.Collections.Concurrent;

namespace CatalogService.Simulated;

/// <summary>
/// In-memory stand-in for an Azure Storage Queue. Messages are plain strings
/// (typically JSON). Tests can inspect <see cref="Messages"/> to assert that an
/// event was published.
/// </summary>
public class SimulatedQueue
{
    private readonly ConcurrentQueue<string> _messages = new();

    public Task SendMessageAsync(string message)
    {
        _messages.Enqueue(message);
        return Task.CompletedTask;
    }

    public IReadOnlyCollection<string> Messages => _messages.ToArray();

    public int Count => _messages.Count;
}
