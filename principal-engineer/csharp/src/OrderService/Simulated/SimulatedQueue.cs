using System.Collections.Concurrent;

namespace OrderService.Simulated;

/// <summary>
/// In-memory stand-in for an Azure Storage Queue. Messages are JSON strings.
/// A read cursor (<see cref="Receive"/>) lets a consumer drain new messages,
/// which the bonus fulfillment saga relies on. Tests can also inspect
/// <see cref="Messages"/> directly.
/// </summary>
public class SimulatedQueue
{
    private readonly List<string> _messages = new();
    private readonly object _gate = new();
    private int _cursor;

    public Task SendMessageAsync(string message)
    {
        lock (_gate) { _messages.Add(message); }
        return Task.CompletedTask;
    }

    /// <summary>Returns messages enqueued since the last Receive call.</summary>
    public Task<IReadOnlyList<string>> ReceiveAsync()
    {
        lock (_gate)
        {
            IReadOnlyList<string> batch = _messages.Skip(_cursor).ToList();
            _cursor = _messages.Count;
            return Task.FromResult(batch);
        }
    }

    public IReadOnlyList<string> Messages
    {
        get { lock (_gate) { return _messages.ToList(); } }
    }

    public int Count { get { lock (_gate) { return _messages.Count; } } }
}
