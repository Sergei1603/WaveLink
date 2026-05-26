using Microsoft.Extensions.Options;
using Minio;
using Minio.DataModel.Args;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

public interface IMinioStorageService
{
    Task EnsureBucketAsync(CancellationToken ct);
    Task UploadAsync(string key, Stream stream, long size, string contentType, CancellationToken ct);
    Task<Stream> OpenReadAsync(string key, CancellationToken ct);
    Task<Stream> OpenRangeAsync(string key, long offset, long length, CancellationToken ct);
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

    public async Task<Stream> OpenRangeAsync(string key, long offset, long length, CancellationToken ct)
    {
        var ms = new MemoryStream();
        await _client.GetObjectAsync(new GetObjectArgs()
            .WithBucket(_options.Bucket)
            .WithObject(key)
            .WithOffsetAndLength(offset, length)
            .WithCallbackStream(async (s, c) => { await s.CopyToAsync(ms, c); }), ct);
        ms.Position = 0;
        return ms;
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
