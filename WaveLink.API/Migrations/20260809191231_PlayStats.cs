using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace WaveLink.API.Migrations
{
    /// <inheritdoc />
    public partial class PlayStats : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "PlayEvents",
                columns: table => new
                {
                    Id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    TrackId = table.Column<Guid>(type: "uuid", nullable: false),
                    ClientEventId = table.Column<Guid>(type: "uuid", nullable: false),
                    StartedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ReportedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ListenedSeconds = table.Column<int>(type: "integer", nullable: false),
                    TrackDuration = table.Column<int>(type: "integer", nullable: false),
                    CompletionPercent = table.Column<double>(type: "double precision", nullable: false),
                    IsSignificant = table.Column<bool>(type: "boolean", nullable: false),
                    IsCompleted = table.Column<bool>(type: "boolean", nullable: false),
                    Source = table.Column<int>(type: "integer", nullable: false),
                    TitleSnapshot = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: false),
                    ArtistSnapshot = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PlayEvents", x => x.Id);
                    table.ForeignKey(
                        name: "FK_PlayEvents_Tracks_TrackId",
                        column: x => x.TrackId,
                        principalTable: "Tracks",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_PlayEvents_Users_UserId",
                        column: x => x.UserId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "UserTrackStats",
                columns: table => new
                {
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    TrackId = table.Column<Guid>(type: "uuid", nullable: false),
                    PlayCount = table.Column<int>(type: "integer", nullable: false),
                    StartCount = table.Column<int>(type: "integer", nullable: false),
                    CompletedCount = table.Column<int>(type: "integer", nullable: false),
                    TotalListenedSeconds = table.Column<long>(type: "bigint", nullable: false),
                    FirstPlayedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    LastPlayedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_UserTrackStats", x => new { x.UserId, x.TrackId });
                    table.ForeignKey(
                        name: "FK_UserTrackStats_Tracks_TrackId",
                        column: x => x.TrackId,
                        principalTable: "Tracks",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_UserTrackStats_Users_UserId",
                        column: x => x.UserId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_PlayEvents_TrackId_StartedAt",
                table: "PlayEvents",
                columns: new[] { "TrackId", "StartedAt" });

            migrationBuilder.CreateIndex(
                name: "IX_PlayEvents_UserId_ClientEventId",
                table: "PlayEvents",
                columns: new[] { "UserId", "ClientEventId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_PlayEvents_UserId_StartedAt",
                table: "PlayEvents",
                columns: new[] { "UserId", "StartedAt" });

            migrationBuilder.CreateIndex(
                name: "IX_UserTrackStats_TrackId",
                table: "UserTrackStats",
                column: "TrackId");

            migrationBuilder.CreateIndex(
                name: "IX_UserTrackStats_UserId_PlayCount",
                table: "UserTrackStats",
                columns: new[] { "UserId", "PlayCount" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "PlayEvents");

            migrationBuilder.DropTable(
                name: "UserTrackStats");
        }
    }
}
