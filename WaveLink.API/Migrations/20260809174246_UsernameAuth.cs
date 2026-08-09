using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WaveLink.API.Migrations
{
    /// <inheritdoc />
    public partial class UsernameAuth : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_Users_Email",
                table: "Users");

            migrationBuilder.RenameColumn(
                name: "Email",
                table: "Users",
                newName: "Username");

            // Existing accounts keep working: the local part of the e-mail becomes the
            // nickname, sanitised to the allowed charset and trimmed to 24 chars so the
            // de-duplication suffix below still fits into the 32-char column.
            migrationBuilder.Sql("""
                UPDATE "Users"
                SET "Username" = left(
                    regexp_replace(split_part("Username", '@', 1), '[^A-Za-z0-9._-]', '_', 'g'), 24);

                UPDATE "Users"
                SET "Username" = 'user_' || left(replace("Id"::text, '-', ''), 8)
                WHERE length("Username") < 3;

                UPDATE "Users" u
                SET "Username" = u."Username" || '_' || left(replace(u."Id"::text, '-', ''), 6)
                WHERE EXISTS (
                    SELECT 1 FROM "Users" x
                    WHERE lower(x."Username") = lower(u."Username") AND x."Id" <> u."Id");
                """);

            migrationBuilder.AlterColumn<string>(
                name: "Username",
                table: "Users",
                type: "character varying(32)",
                maxLength: 32,
                nullable: false,
                oldClrType: typeof(string),
                oldType: "character varying(256)",
                oldMaxLength: 256);

            migrationBuilder.CreateIndex(
                name: "IX_Users_Username",
                table: "Users",
                column: "Username",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_Users_Username",
                table: "Users");

            migrationBuilder.AlterColumn<string>(
                name: "Username",
                table: "Users",
                type: "character varying(256)",
                maxLength: 256,
                nullable: false,
                oldClrType: typeof(string),
                oldType: "character varying(32)",
                oldMaxLength: 32);

            migrationBuilder.RenameColumn(
                name: "Username",
                table: "Users",
                newName: "Email");

            migrationBuilder.CreateIndex(
                name: "IX_Users_Email",
                table: "Users",
                column: "Email",
                unique: true);
        }
    }
}
