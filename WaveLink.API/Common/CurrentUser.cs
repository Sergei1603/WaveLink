using System.Security.Claims;

namespace WaveLink.API.Common;

public static class CurrentUser
{
    public const string UserIdClaim = "sub";
    public const string UsernameClaim = "username";

    public static Guid GetId(ClaimsPrincipal principal)
    {
        var raw = principal.FindFirst(UserIdClaim)?.Value
                  ?? principal.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? throw AppException.Unauthorized();
        return Guid.Parse(raw);
    }
}
