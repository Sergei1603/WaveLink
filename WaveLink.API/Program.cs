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
builder.Services.Configure<JwtOptions>(builder.Configuration.GetSection(JwtOptions.SectionName));
builder.Services.Configure<MinioOptions>(builder.Configuration.GetSection(MinioOptions.SectionName));
builder.Services.Configure<TelegramOptions>(builder.Configuration.GetSection(TelegramOptions.SectionName));

var jwt = builder.Configuration.GetSection(JwtOptions.SectionName).Get<JwtOptions>()
          ?? throw new InvalidOperationException("Jwt configuration missing");
var minio = builder.Configuration.GetSection(MinioOptions.SectionName).Get<MinioOptions>()
            ?? throw new InvalidOperationException("Minio configuration missing");

// ---------- EF Core ----------
var connectionString = builder.Configuration.GetConnectionString("Postgres")!;

// Render передаёт строку в формате postgresql://, конвертируем в ADO.NET
if (connectionString.StartsWith("postgresql://") || connectionString.StartsWith("postgres://"))
{
    var uri = new Uri(connectionString);
    var userInfo = uri.UserInfo.Split(':');
    var host = uri.Host;
    var port = uri.Port > 0 ? uri.Port : 5432; // fallback если порт не указан
    var database = uri.AbsolutePath.TrimStart('/');
    var username = userInfo[0];
    var password = Uri.UnescapeDataString(userInfo[1]); // на случай спецсимволов в пароле

    connectionString = $"Host={host};Port={port};Database={database};Username={username};Password={password};SSL Mode=Require;Trust Server Certificate=true";
}
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(connectionString));

// ---------- MinIO ----------
builder.Services.AddSingleton<IMinioClient>(_ =>
{
    var client = new MinioClient()
        .WithEndpoint(minio.Endpoint)
        .WithCredentials(minio.AccessKey, minio.SecretKey);
    if (minio.UseSsl) client = client.WithSSL();
    return client.Build();
});

// ---------- App services ----------
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddScoped<ITrackService, TrackService>();
builder.Services.AddScoped<ICollectionService, CollectionService>();
builder.Services.AddScoped<IMinioStorageService, MinioStorageService>();
builder.Services.AddScoped<ITelegramLinkTokenService, TelegramLinkTokenService>();
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

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
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
