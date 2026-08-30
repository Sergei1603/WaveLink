package ru.wavelink.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.wavelink.app.core.net.SendToTelegramBody
import ru.wavelink.app.core.net.TelegramStatusDto
import ru.wavelink.app.core.net.WaveLinkApi
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.library.BulkResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web → chat delivery only. Pairing a chat with an account stays a web-and-bot job (the server's
 * `POST /api/telegram/link` answers 410 on purpose), so this client never tries to link.
 *
 * The status is cached in a singleton because three screens ask about it — the player, the track
 * card and the profile — and none of them should each fire their own request.
 */
@Singleton
class TelegramRepository @Inject constructor(private val api: WaveLinkApi) {

    private val _status = MutableStateFlow<TelegramStatusDto?>(null)
    val status: StateFlow<TelegramStatusDto?> = _status.asStateFlow()

    /** Best-effort: an unreachable server simply means the ✈ actions stay hidden. */
    suspend fun refresh() {
        runCatching { api.telegramStatus() }.onSuccess { _status.value = it }
    }

    suspend fun send(trackId: String) = api.sendToTelegram(SendToTelegramBody(trackId))

    /**
     * One request per track: the API takes a single track id, and the bot posts one audio
     * message per file anyway. Failures are counted, not thrown, so a chat that rejects one
     * oversized file still receives the rest.
     */
    suspend fun sendMany(trackIds: List<String>): BulkResult {
        var ok = 0
        var failed = 0
        var firstError: String? = null
        for (id in trackIds) {
            runCatching { send(id) }
                .onSuccess { ok++ }
                .onFailure {
                    failed++
                    if (firstError == null) firstError = it.toUserMessage()
                }
        }
        return BulkResult(ok, failed, firstError)
    }
}
