FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY WaveLink.API/WaveLink.API.csproj WaveLink.API/
COPY nuget.config* ./
RUN dotnet restore WaveLink.API/WaveLink.API.csproj

COPY WaveLink.API/ WaveLink.API/
RUN dotnet publish WaveLink.API/WaveLink.API.csproj \
    -c Release -o /app /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
COPY --from=build /app .

ENV ASPNETCORE_ENVIRONMENT=Production
ENV DOTNET_RUNNING_IN_CONTAINER=true

# Render injects $PORT at runtime — bind ASP.NET to it via shell form
ENTRYPOINT ["sh", "-c", "exec dotnet WaveLink.API.dll --urls=http://+:${PORT:-8080}"]
