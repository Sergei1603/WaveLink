using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WaveLink.API.Migrations
{
    /// <inheritdoc />
    public partial class PublicBank : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "IsDeletedByOwner",
                table: "Tracks",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "IsPublic",
                table: "Tracks",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.CreateTable(
                name: "SavedTracks",
                columns: table => new
                {
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    TrackId = table.Column<Guid>(type: "uuid", nullable: false),
                    SavedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_SavedTracks", x => new { x.UserId, x.TrackId });
                    table.ForeignKey(
                        name: "FK_SavedTracks_Tracks_TrackId",
                        column: x => x.TrackId,
                        principalTable: "Tracks",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_SavedTracks_Users_UserId",
                        column: x => x.UserId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_Tracks_IsPublic_IsDeletedByOwner",
                table: "Tracks",
                columns: new[] { "IsPublic", "IsDeletedByOwner" });

            migrationBuilder.CreateIndex(
                name: "IX_SavedTracks_TrackId",
                table: "SavedTracks",
                column: "TrackId");

            migrationBuilder.CreateIndex(
                name: "IX_SavedTracks_UserId",
                table: "SavedTracks",
                column: "UserId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "SavedTracks");

            migrationBuilder.DropIndex(
                name: "IX_Tracks_IsPublic_IsDeletedByOwner",
                table: "Tracks");

            migrationBuilder.DropColumn(
                name: "IsDeletedByOwner",
                table: "Tracks");

            migrationBuilder.DropColumn(
                name: "IsPublic",
                table: "Tracks");
        }
    }
}
