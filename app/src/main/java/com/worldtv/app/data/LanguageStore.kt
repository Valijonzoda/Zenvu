package com.worldtv.app.data

import android.content.Context
import android.content.res.Configuration
import com.worldtv.app.domain.AppLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object LocaleApplier {
    const val PREFS = "zenvu_language"
    const val KEY = "tag"

    fun read(context: Context): String? = "ru"

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag("ru")
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

class LanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        LocaleApplier.PREFS,
        Context.MODE_PRIVATE,
    )
    private val tagState = MutableStateFlow(LocaleApplier.read(context.applicationContext))
    val tag: StateFlow<String?> = tagState.asStateFlow()

    fun select(tag: String) {
        val language = AppLanguages.find(tag) ?: return
        prefs.edit().putString(LocaleApplier.KEY, language.tag).commit()
        Locale.setDefault(Locale.forLanguageTag(language.tag))
        tagState.value = language.tag
    }
}
