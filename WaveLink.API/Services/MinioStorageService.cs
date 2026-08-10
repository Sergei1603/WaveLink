using Microsoft.Extensions.Options;
using Minio;
using Minio.DataModel.Args;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

public interface IMinioStorageService
{
    Task EnsureBucketAsync(CancellationToken ct);
    Task UploadAsync(string key, Stream stream, long size, string contentType, CancellationToken ct);
    /// <summary>
    /// Buffers the whole object into memory. Only for callers that need a seekable stream
    /// (Telegram audio delivery); for HTTP responses use <see cref="CopyToAsync"/>.
    /// </summary>
    Task<Stream> OpenReadAsync(string key, CancellationToken ct);

    /// <summary>
    /// Streams the object (or a byte range of it) straight into <paramref name="destination"/>
    /// without buffering it in memory.
    /// </summary>
    Task CopyToAsync(string key, long? offset, long? length, Stream destination, CancellationToken ct);
    Task<long> GetSizeAsync(string key, CancellationToken ct);
    Task DeleteAsync(string key, CancellationToken ct);
    Task<string> GetPresignedGetUrlAsync(string key, CancellationToken ct);
}

public class MinioStorageService : IMinioStorageService
{
    private readonly IMinioClient _client;
    private readonly MinioOptions _options;
    private readonly ILogger<MinioStorageService> _logger;

    public MinioStorageService(IMinioClient client, IOptions<MinioOptions> options, ILogger<MinioStorageService> logger)
    {
        _client = client;
        _options = options.Value;
        _logger = logger;
    }

    public async Task EnsureBucketAsync(CancellationToken ct)
    {
        var exists = await _client.BucketExistsAsync(
            new BucketExistsArgs().WithBucket(_options.Bucket), ct);
        if (!exists)
        {
            await _client.MakeBucketAsync(new MakeBucketArgs().WithBucket(_options.Bucket), ct);
            _logger.LogInformation("Created MinIO bucket {Bucket}", _options.Bucket);
        }
    }

    public async Task UploadAsync(string key, Stream stream, long size, string contentType, CancellationToken ct)
    {
        await _client.PutObjectAsync(new PutObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key)
            .WithStreamData(stream)
            .WithObjectSize(size)
            .WithContentType(contentType), ct);
    }

    public async Task<Stream> OpenReadAsync(string key, CancellationToken ct)
    {
        var ms = new MemoryStream();
        await _client.GetObjectAsync(new GetObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key)
            .WithCallbackStream(async (s, c) => { await s.CopyToAsync(ms, c); }), ct);
        ms.Position = 0;
        return ms;
    }

    public async Task CopyToAsync(string key, long? offset, long? length, Stream destination, CancellationToken ct)
    {
        var args = new GetObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key)
            .WithCallbackStream(async (s, c) => { await s.CopyToAsync(destination, 81920, c); });

        if (offset.HasValue && length.HasValue)
            args = args.WithOffsetAndLength(offset.Value, length.Value);

        await _client.GetObjectAsync(args, ct);
    }

    public async Task<long> GetSizeAsync(string key, CancellationToken ct)
    {
        var stat = await _client.StatObjectAsync(new StatObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key), ct);
        return stat.Size;
    }

    public async Task DeleteAsync(string key, CancellationToken ct)
    {
        await _client.RemoveObjectAsync(new RemoveObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key), ct);
    }

    public async Task<string> GetPresignedGetUrlAsync(string key, CancellationToken ct)
    {
        return await _client.PresignedGetObjectAsync(new PresignedGetObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key)
            .WithExpiry(_options.PresignedUrlMinutes * 60));
    }
}
