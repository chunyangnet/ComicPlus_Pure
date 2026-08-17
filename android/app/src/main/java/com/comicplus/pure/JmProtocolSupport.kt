package com.comicplus.pure

import org.json.JSONObject

internal fun mergeHomeRankings(
    priorityItems: List<JmRanking>,
    secondaryItems: List<JmRanking>,
): List<JmRanking> = (priorityItems + secondaryItems).distinctBy(JmRanking::id).take(40)

/**
 * Some official hosts decrypt a ranking request successfully but return an empty or unrelated
 * payload for time-limited charts. Treat only a structurally usable first page as feature support
 * so the gateway can continue with another official host.
 */
internal fun JSONObject.hasUsableRankingPayload(key: String): Boolean {
    val root = optJSONObject("data") ?: this
    return root.array(key).objectsOrValues(50).any { value ->
        val item = value as? JSONObject ?: return@any false
        item.string("id").matches(safeNumericId) && item.string("name").isNotBlank()
    }
}

internal fun JSONObject.hasUsableWeekCatalogPayload(): Boolean {
    val root = optJSONObject("data") ?: this
    val categories = root.array("categories").objectsOrValues(50)
    val types = (root.optJSONArray("type") ?: root.optJSONArray("types"))
        ?.objectsOrValues(50)
        .orEmpty()
    return categories.any(::hasOptionId) && types.any(::hasOptionId)
}

private fun hasOptionId(value: Any?): Boolean =
    (value as? JSONObject)?.string("id")?.isNotBlank() == true

private const val LOWER_HEX_DIGITS = "0123456789abcdef"

internal fun encodeLowerHex(bytes: ByteArray): String {
    val output = CharArray(bytes.size * 2)
    bytes.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        output[index * 2] = LOWER_HEX_DIGITS[value ushr 4]
        output[index * 2 + 1] = LOWER_HEX_DIGITS[value and 0x0f]
    }
    return String(output)
}
