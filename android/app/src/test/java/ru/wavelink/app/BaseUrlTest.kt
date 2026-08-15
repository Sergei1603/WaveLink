package ru.wavelink.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wavelink.app.core.net.BaseUrlInterceptor
import ru.wavelink.app.core.prefs.SettingsStore

/**
 * The server address is typed on the sign-in screen, so a bad normalisation locks the user out
 * of an app they cannot yet reach — nothing downstream can recover from it.
 */
class BaseUrlTest {

    @Test
    fun `bare host becomes plain http with a trailing slash`() {
        assertEquals("http://193.222.99.254/", SettingsStore.normalizeBaseUrl("193.222.99.254"))
    }

    @Test
    fun `an explicit scheme is kept`() {
        assertEquals("https://wavelink.ru/", SettingsStore.normalizeBaseUrl("https://wavelink.ru"))
    }

    @Test
    fun `surrounding whitespace and a missing slash are fixed`() {
        assertEquals(
            "http://192.168.0.10:5000/",
            SettingsStore.normalizeBaseUrl("  192.168.0.10:5000  ")
        )
    }

    @Test
    fun `hosts with and without a port and path are valid`() {
        assertTrue(SettingsStore.isValidBaseUrl("193.222.99.254"))
        assertTrue(SettingsStore.isValidBaseUrl("192.168.0.10:5000"))
        assertTrue(SettingsStore.isValidBaseUrl("https://wavelink.ru/music"))
    }

    @Test
    fun `blank input and stray spaces are rejected`() {
        assertFalse(SettingsStore.isValidBaseUrl(""))
        assertFalse(SettingsStore.isValidBaseUrl("   "))
        assertFalse(SettingsStore.isValidBaseUrl("192.168.0.10 5000"))
    }

    @Test
    fun `the placeholder host is replaced by the configured one`() {
        val rewritten = BaseUrlInterceptor.rewrite(
            "http://wavelink.invalid/api/tracks?page=2".toHttpUrl(),
            "http://193.222.99.254/".toHttpUrl()
        )
        assertEquals("http://193.222.99.254/api/tracks?page=2", rewritten.toString())
    }

    @Test
    fun `a path on the configured address is kept as a prefix`() {
        val rewritten = BaseUrlInterceptor.rewrite(
            "http://wavelink.invalid/api/auth/login".toHttpUrl(),
            "https://example.com/wavelink/".toHttpUrl()
        )
        assertEquals("https://example.com/wavelink/api/auth/login", rewritten.toString())
    }
}
