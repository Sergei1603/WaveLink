package ru.wavelink.app.core.net

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The subset of the WaveLink API this client uses. Uploading audio and Telegram linking are
 * deliberately out of scope for the mobile app.
 *
 * Auth endpoints carry [NO_AUTH] so the token authenticator cannot recurse into them.
 */
interface WaveLinkApi {

    @POST("api/auth/register")
    suspend fun register(@Header(NO_AUTH) noAuth: String = "1", @Body body: AuthBody): TokenPairDto

    @POST("api/auth/login")
    suspend fun login(@Header(NO_AUTH) noAuth: String = "1", @Body body: AuthBody): TokenPairDto

    @POST("api/auth/refresh")
    suspend fun refresh(@Header(NO_AUTH) noAuth: String = "1", @Body body: RefreshBody): TokenPairDto

    @POST("api/auth/logout")
    suspend fun logout(@Header(NO_AUTH) noAuth: String = "1", @Body body: RefreshBody)

    @GET("api/tracks")
    suspend fun tracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("sort") sort: String = "recent"
    ): PagedDto<TrackDto>

    @GET("api/tracks/public")
    suspend fun publicTracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "recent"
    ): PagedDto<TrackDto>

    @GET("api/tracks/{id}")
    suspend fun track(@Path("id") id: String): TrackDetailDto

    @GET("api/tracks/shuffle")
    suspend fun shuffle(
        @Query("mode") mode: String,
        @Query("limit") limit: Int = 50,
        @Query("collectionId") collectionId: String? = null
    ): List<TrackDto>

    @PATCH("api/tracks/{id}")
    suspend fun updateTrack(@Path("id") id: String, @Body body: UpdateTrackBody): TrackDto

    @POST("api/tracks/{id}/save")
    suspend fun saveTrack(@Path("id") id: String)

    @DELETE("api/tracks/{id}/save")
    suspend fun unsaveTrack(@Path("id") id: String)

    @DELETE("api/tracks/{id}")
    suspend fun deleteTrack(@Path("id") id: String)

    @GET("api/collections")
    suspend fun collections(): List<CollectionDto>

    @GET("api/collections/{id}")
    suspend fun collection(@Path("id") id: String): CollectionDetailDto

    @POST("api/collections")
    suspend fun createCollection(@Body body: NameBody): CollectionDto

    @POST("api/collections/{id}/tracks")
    suspend fun addToCollection(@Path("id") id: String, @Body body: TrackIdBody)

    @DELETE("api/collections/{collectionId}/tracks/{trackId}")
    suspend fun removeFromCollection(
        @Path("collectionId") collectionId: String,
        @Path("trackId") trackId: String
    )

    @DELETE("api/collections/{id}")
    suspend fun deleteCollection(@Path("id") id: String)

    @POST("api/plays")
    suspend fun reportPlays(@Body body: ReportPlaysBody): ReportPlaysResponseDto

    @GET("api/stats/me")
    suspend fun myStats(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int = 20
    ): MyStatsDto

    @GET("api/stats/artist")
    suspend fun artistStats(@Query("name") name: String): ArtistStatsDto

    @GET("api/telegram/status")
    suspend fun telegramStatus(): TelegramStatusDto

    @POST("api/telegram/send")
    suspend fun sendToTelegram(@Body body: SendToTelegramBody)

    companion object {
        /** Marker header stripped by [AuthInterceptor]; marks requests that must stay unauthenticated. */
        const val NO_AUTH = "X-WaveLink-No-Auth"
    }
}
