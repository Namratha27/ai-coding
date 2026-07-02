namespace OrderService.Services;

/// <summary>
/// Transport-agnostic service result. The HTTP layer maps <see cref="Status"/>
/// to a status code.
/// </summary>
public record ServiceResult<T>(int Status, T? Value = default, string? Error = null)
{
    public static ServiceResult<T> Ok(T value) => new(200, value);
    public static ServiceResult<T> Created(T value) => new(201, value);
    public static ServiceResult<T> NoContent() => new(204);
    public static ServiceResult<T> BadRequest(string message) => new(400, default, message);
    public static ServiceResult<T> NotFound(string message = "Not found") => new(404, default, message);
    public static ServiceResult<T> Conflict(string message) => new(409, default, message);
}
