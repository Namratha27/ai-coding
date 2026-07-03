using System.Collections.Concurrent;

namespace CatalogService.Simulated;

/// <summary>
/// In-memory stand-in for an Azure Blob Storage container. Returns a fake but
/// stable blob URL so the rest of the system can reference uploaded content.
/// </summary>
public class SimulatedBlobContainer
{
    private readonly ConcurrentDictionary<string, byte[]> _blobs = new();
    private const string BaseUrl = "https://sim.blob.local/product-images";

    public Task<string> UploadAsync(string blobName, byte[] content)
    {
        _blobs[blobName] = content;
        return Task.FromResult($"{BaseUrl}/{blobName}");
    }

    public Task<byte[]?> DownloadAsync(string blobName)
    {
        _blobs.TryGetValue(blobName, out var content);
        return Task.FromResult(content);
    }

    public bool Exists(string blobName) => _blobs.ContainsKey(blobName);

    public int Count => _blobs.Count;
}
