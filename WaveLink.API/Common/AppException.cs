namespace WaveLink.API.Common;

public class AppException : Exception
{
    public int StatusCode { get; }

    public AppException(string message, int statusCode = 400) : base(message)
    {
        StatusCode = statusCode;
    }

    public static AppException NotFound(string what) => new($"{what} not found", 404);
    public static AppException Unauthorized(string msg = "Unauthorized") => new(msg, 401);
    public static AppException Forbidden(string msg = "Forbidden") => new(msg, 403);
    public static AppException Conflict(string msg) => new(msg, 409);
}
