namespace WaveLink.API.Options;

public class MinioOptions
{
    public const string SectionName = "Minio";

    public string Endpoint { get; set; } = "localhost:9000";
    public string AccessKey { get; set; } = null!;
    public string SecretKey { get; set; } = null!;
    public string Bucket { get; set; } = "wavelink-tracks";
    public bool UseSsl { get; set; } = false;
    public int PresignedUrlMinutes { get; set; } = 15;
}
