using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Minio;
using Scalar.AspNetCore;
using WaveLink.API.Data;
using WaveLink.API.Middleware;
using WaveLink.API.Options;
using WaveLink.API.Services;

var builder = WebApplication.CreateBuilder(args);

// ---------- Configuration / Options ----------
// Render/контейнер задаёт порт через переменную PORT — слушаем его на всех интерфейсах.
// Локально PORT не задан → используем applicationUrl из launchSettings.json (5000).
var port1 = Environment.GetEnvironmentVariable("PORT");
if (!string.IsNullOrEmpty(port1))
{
    builder.WebHost.UseUrls($"http://0.0.0.0:{port1}");
}

builder.Services.Configure<JwtOptions>(builder.Configuration.GetSection(JwtOptions.SectionName));
builder.Services.Configure<MinioOptions>(builder.Configuration.GetSection(MinioOptions.SectionName));
builder.Services.Configure<TelegramOptions>(builder.Configuration.GetSection(TelegramOptions.SectionName));
builder.Services.Configure<PlayStatsOptions>(builder.Configuration.GetSection(PlayStatsOptions.SectionName));

var jwt = builder.Configuration.GetSection(JwtOptions.SectionName).Get<JwtOptions>()
          ?? throw new InvalidOperationException("Jwt configuration missing");
var minio = builder.Configuration.GetSection(MinioOptions.SectionName).Get<MinioOptions>()
            ?? throw new InvalidOperationException("Minio configuration missing");

// ---------- EF Core ----------
var connectionString = builder.Configuration.GetConnectionString("Postgres")!;

if (connectionString.StartsWith("postgresql://") || connectionString.StartsWith("postgres://"))
{
    var normalized = connectionString
        .Replace("postgresql://", "http://")
        .Replace("postgres://", "http://");

    var uri = new Uri(normalized);
    var userInfo = uri.UserInfo.Split(':', 2);
    var host = uri.Host;
    var port = uri.IsDefaultPort ? 5432 : uri.Port;   // ← фикс порта 80
    var database = uri.AbsolutePath.TrimStart('/');
    var username = Uri.UnescapeDataString(userInfo[0]);
    var password = Uri.UnescapeDataString(userInfo[1]);

    connectionString = $"Host={host};Port={port};Database={database};Username={username};Password={password};SSL Mode=Require;Trust Server Certificate=true";
    Console.WriteLine($"[DEBUG] host={host}, port={port}, db={database}");
}
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(connectionString));

// ---------- MinIO ----------
builder.Services.AddSingleton<IMinioClient>(_ =>
{
    var client = new MinioClient()
        .WithEndpoint(minio.Endpoint)
        .WithCredentials(minio.AccessKey, minio.SecretKey)
        .WithRegion("ru-central1");
    if (minio.UseSsl) client = client.WithSSL();
    return client.Build();
});

// ---------- App services ----------
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddScoped<ITrackService, TrackService>();
builder.Services.AddScoped<ICollectionService, CollectionService>();
builder.Services.AddScoped<IPlayStatsService, PlayStatsService>();
builder.Services.AddScoped<IMinioStorageService, MinioStorageService>();
builder.Services.AddScoped<ITelegramLinkTokenService, TelegramLinkTokenService>();
builder.Services.AddSingleton<ITelegramClientProvider, TelegramClientProvider>();
builder.Services.AddScoped<ITelegramDeliveryService, TelegramDeliveryService>();
builder.Services.AddHostedService<TelegramBotService>();

// ---------- Auth ----------
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(o =>
    {
        o.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwt.Issuer,
            ValidAudience = jwt.Audience,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt.Secret)),
            ClockSkew = TimeSpan.FromSeconds(30),
            NameClaimType = "sub"
        };
    });
builder.Services.AddAuthorization();

// ---------- CORS ----------
const string CorsPolicy = "WaveLinkClient";
var allowedOrigins = (builder.Configuration["Cors:AllowedOrigins"] ?? "http://localhost:5173")
    .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
builder.Services.AddCors(o => o.AddPolicy(CorsPolicy, p =>
    p.WithOrigins(allowedOrigins)
     .AllowAnyHeader()
     .AllowAnyMethod()
     .WithExposedHeaders("Content-Range", "Accept-Ranges", "Content-Length")));

// ---------- MVC + OpenAPI ----------
builder.Services.AddControllers();
builder.Services.AddOpenApi();

var app = builder.Build();

// ---------- Pipeline ----------
app.UseMiddleware<ErrorHandlingMiddleware>();
app.MapOpenApi();

if (app.Environment.IsDevelopment())
{
    app.MapScalarApiReference();
}

app.UseCors(CorsPolicy);
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();

// ---------- Startup tasks ----------
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.MigrateAsync();

    var storage = scope.ServiceProvider.GetRequiredService<IMinioStorageService>();
    try { await storage.EnsureBucketAsync(CancellationToken.None); }
    catch (Exception ex)
    {
        app.Logger.LogWarning(ex, "Could not ensure MinIO bucket on startup; will retry on first use");
    }
}

app.Run();
