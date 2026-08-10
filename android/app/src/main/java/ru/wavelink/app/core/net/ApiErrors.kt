package ru.wavelink.app.core.net

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Turns a thrown API failure into something worth showing a user. The server's uniform
 * `{ error, statusCode }` envelope carries a human-readable Russian message; anything else
 * falls back to a generic line.
 */
fun Throwable.toUserMessage(json: Json = DefaultJson): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { json.decodeFromString<ErrorDto>(it) }.getOrNull() }
        parsed?.error ?: "Ошибка сервера (${code()})"
    }
    is IOException -> "Нет связи с сервером"
    else -> message ?: "Что-то пошло не так"
}

fun Throwable.isOffline(): Boolean = this is IOException

private val DefaultJson = Json { ignoreUnknownKeys = true }
