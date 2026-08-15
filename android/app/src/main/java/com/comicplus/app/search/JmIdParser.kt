package com.comicplus.app.search

data class JmSearchIntent(
    val query: String,
    val sourceId: String? = null,
    val exact: Boolean = false,
)

object JmIdParser {
    private val explicitPattern = Regex(
        pattern = "^jm\\s*[:#-]?\\s*(\\d{1,12})$",
        option = RegexOption.IGNORE_CASE,
    )
    private val digitsPattern = Regex("^\\d{1,12}$")

    fun parse(rawQuery: String): JmSearchIntent {
        val query = rawQuery.trim()
        explicitPattern.matchEntire(query)?.let { match ->
            return JmSearchIntent(query = query, sourceId = match.groupValues[1], exact = true)
        }
        if (digitsPattern.matches(query)) {
            return JmSearchIntent(query = query, sourceId = query, exact = false)
        }
        return JmSearchIntent(query = query)
    }
}

