package com.worldtv.app.domain

data class AppLanguage(
    val tag: String,
    val nativeName: String,
)

object AppLanguages {
    val all: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("ru", "Русский"),
        AppLanguage("es", "Español"),
        AppLanguage("pt-BR", "Português"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("ar", "العربية"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("zh-CN", "中文"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("uk", "Українська"),
        AppLanguage("pl", "Polski"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("it", "Italiano"),
        AppLanguage("th", "ไทย"),
        AppLanguage("fa", "فارسی"),
        AppLanguage("nl", "Nederlands"),
    )

    fun find(tag: String?): AppLanguage? {
        if (tag.isNullOrBlank()) return null
        return all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
    }

    /**
     * Outfit covers Latin scripts used by these languages.
     * Other languages use the system font so their letters stay readable.
     */
    fun usesOutfit(tag: String?): Boolean {
        val language = tag?.substringBefore('-') ?: return true
        return language in outfitLanguages
    }

    private val outfitLanguages = setOf(
        "en", "es", "pt", "fr", "de", "tr", "id", "pl", "it", "nl",
    )
}
