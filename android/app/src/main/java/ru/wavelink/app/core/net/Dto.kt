package ru.wavelink.app.core.net

import kotlinx.serialization.Serializable

/**
 * Wire shapes mirroring `WaveLink.API/DTOs`. Timestamps stay as the raw ISO-8601 strings the
 * server sends; they are parsed at the edges that actually need a point in time.
 */

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String
)

@Serializable
data class AuthBody(val username: String, val password: String)

@Serializable
data class RefreshBody(val refreshToken: String)

@Serializable
data class ErrorDto(val error: String? = null, val statusCode: Int = 0)

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val fileSize: Long,
    val mimeType: String,
    val uploadedAt: String,
    val isPublic: Boolean,
    val isOwned: Boolean,
    val uploaderId: String,
    val uploaderUsername: String,
    val myPlays: Int,
    val myLastPlayedAt: String? = null,
    val myCompleted: Boolean
)

@Serializable
data class PagedDto<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int
)

@Serializable
data class TrackStatsDto(
    val trackId: String,
    val totalPlays: Int,
    val distinctListeners: Int,
    val myPlays: Int,
    val myLastPlayedAt: String? = null,
    val myCompleted: Boolean,
    val myListenedSeconds: Long
)

@Serializable
data class TrackDetailDto(val track: TrackDto, val stats: TrackStatsDto)

@Serializable
data class UpdateTrackBody(
    val title: String? = null,
    val artist: String? = null,
    val isPublic: Boolean? = null
)

@Serializable
data class CollectionDto(
    val id: String,
    val name: String,
    val createdAt: String,
    val trackCount: Int
)

@Serializable
data class CollectionDetailDto(
    val id: String,
    val name: String,
    val createdAt: String,
    val tracks: List<TrackDto>
)

@Serializable
data class NameBody(val name: String)

@Serializable
data class TrackIdBody(val trackId: String)

// ---- listening statistics ----

@Serializable
data class ReportPlayItemDto(
    val clientEventId: String,
    val trackId: String,
    val startedAt: String,
    val listenedSeconds: Double,
    val trackDuration: Int? = null,
    /** No default on purpose: kotlinx.serialization omits properties that equal their default,
     *  which would drop the field and make the server record these plays as coming from the web. */
    val source: String
)

@Serializable
data class ReportPlaysBody(val events: List<ReportPlayItemDto>)

@Serializable
data class ReportPlayResultDto(
    val clientEventId: String,
    val status: String,
    val reason: String? = null
)

@Serializable
data class ReportPlaysResponseDto(
    val results: List<ReportPlayResultDto>,
    val accepted: Int,
    val duplicates: Int,
    val rejected: Int
)

@Serializable
data class TopTrackDto(
    val trackId: String,
    val title: String,
    val artist: String,
    val plays: Int,
    val listenedSeconds: Long,
    val lastPlayedAt: String
)

@Serializable
data class TopArtistDto(
    val artist: String,
    val plays: Int,
    val trackCount: Int,
    val listenedSeconds: Long
)

@Serializable
data class MyStatsDto(
    val from: String? = null,
    val to: String? = null,
    val totalPlays: Int,
    val distinctTracks: Int,
    val totalListenedSeconds: Long,
    val completedPlays: Int,
    val topTracks: List<TopTrackDto>,
    val topArtists: List<TopArtistDto>
)

@Serializable
data class ArtistStatsDto(
    val artist: String,
    val totalPlays: Int,
    val distinctListeners: Int,
    val trackCount: Int,
    val myPlays: Int,
    val myListenedSeconds: Long
)
