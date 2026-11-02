package com.worldtv.app.domain

object CountryFlags {
    /**
     * Regional-indicator flag for an ISO country code.
     * The dataset uses `uk` for the United Kingdom; the flag glyph uses `GB`.
     */
    fun emoji(countryCode: String): String {
        val mapped = if (countryCode.equals("uk", ignoreCase = true)) "gb" else countryCode
        if (mapped.length != 2 || mapped.any { !it.isLetter() }) return "🏳️"
        val upper = mapped.uppercase()
        val first = REGIONAL_BASE + (upper[0] - 'A')
        val second = REGIONAL_BASE + (upper[1] - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private const val REGIONAL_BASE = 0x1F1E6
}
