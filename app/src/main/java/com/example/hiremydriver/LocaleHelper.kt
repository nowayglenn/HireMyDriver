package com.example.hiremydriver

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    fun onAttach(context: Context): Context = localizedContext(context, getLanguage(context))

    fun getLanguage(context: Context): String = AppLanguage.normalize(
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(SELECTED_LANGUAGE, Locale.getDefault().language)
    )

    fun setLocale(context: Context, language: String?): Context {
        val code = AppLanguage.normalize(language)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString(SELECTED_LANGUAGE, code).apply()
        return localizedContext(context, code)
    }

    fun languageName(context: Context): String = context.getString(when (getLanguage(context)) {
        "tl" -> R.string.lang_tagalog
        "ilo" -> R.string.lang_ilocano
        else -> R.string.lang_english
    })

    private fun localizedContext(context: Context, code: String): Context {
        val locale = Locale.forLanguageTag(code)
        // Preserve font scale and screen metrics without mutating the shared configuration
        // or the process locale used for booking dates and stored values.
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
