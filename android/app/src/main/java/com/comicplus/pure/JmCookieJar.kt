package com.comicplus.pure

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

internal class MemoryCookieJar(
    private val onCookieUpdated: (Cookie) -> Unit = {},
) : CookieJar {
    private val values = ConcurrentHashMap<String, List<Cookie>>()

    fun clear() {
        values.clear()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        save(url, cookies, notifyUpdates = true)
    }

    fun install(url: HttpUrl, cookies: List<Cookie>) {
        save(url, cookies, notifyUpdates = false)
    }

    private fun save(url: HttpUrl, cookies: List<Cookie>, notifyUpdates: Boolean) {
        val now = System.currentTimeMillis()
        val boundedIncoming = cookies.filter { cookie ->
            cookie.name.length <= MAX_COOKIE_NAME_LENGTH &&
                cookie.value.length <= MAX_COOKIE_VALUE_LENGTH &&
                cookie.domain.length <= MAX_COOKIE_DOMAIN_LENGTH &&
                cookie.path.length <= MAX_COOKIE_PATH_LENGTH
        }
        values.compute(url.host) { _, existing ->
            val replacements = boundedIncoming.mapTo(HashSet(boundedIncoming.size)) { cookie -> cookie.identity() }
            (existing.orEmpty().filterNot { it.identity() in replacements } + boundedIncoming)
                .filter { it.expiresAt > now }
                .takeLast(MAX_COOKIES_PER_HOST)
        }
        if (notifyUpdates) {
            boundedIncoming.forEach { cookie ->
                if (cookie.expiresAt > now) onCookieUpdated(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val current = values[url.host].orEmpty()
        val valid = current.filter { it.expiresAt > now }
        if (valid.size != current.size) values.replace(url.host, current, valid)
        return valid.filter { it.matches(url) }
    }
}

private const val MAX_COOKIES_PER_HOST = 64
private const val MAX_COOKIE_NAME_LENGTH = 128
private const val MAX_COOKIE_VALUE_LENGTH = 4 * 1024
private const val MAX_COOKIE_DOMAIN_LENGTH = 253
private const val MAX_COOKIE_PATH_LENGTH = 512

private fun Cookie.identity(): String = "$name|$domain|$path"
