package com.example.hiremydriver

internal object AppLanguage {
    fun normalize(code: String?): String = when (code?.substringBefore('-')?.substringBefore('_')?.lowercase(java.util.Locale.ROOT)) {
        "tl", "fil" -> "tl"
        "ilo" -> "ilo"
        else -> "en"
    }
}
