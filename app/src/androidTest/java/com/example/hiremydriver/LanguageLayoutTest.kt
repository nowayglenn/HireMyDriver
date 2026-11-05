package com.example.hiremydriver

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LanguageLayoutTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun translatedLanguageRowsAndHomeHeadingFitSmallScreensAndLargeFonts() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (code in listOf("en", "tl", "ilo")) for (width in listOf(320, 360, 411)) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    val config = Configuration(context.resources.configuration).apply {
                        setLocales(LocaleList(Locale.forLanguageTag(code)))
                        fontScale = scale
                        screenWidthDp = width
                    }
                    val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_HireMyDriver)
                    fun inflate(layout: Int): View = LayoutInflater.from(themed).inflate(layout, null).apply {
                        val density = resources.displayMetrics.density
                        measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec((720 * density).toInt(), View.MeasureSpec.EXACTLY))
                        layout(0, 0, measuredWidth, measuredHeight)
                    }
                    val language = inflate(R.layout.activity_language_settings)
                    val rows = listOf(R.id.rbEnglish, R.id.rbTagalog, R.id.rbIlocano).map { language.findViewById<TextView>(it) }
                    rows.forEach { row ->
                        assertTrue("$code $width $scale language text clipped", row.layout.height <= row.height - row.compoundPaddingTop - row.compoundPaddingBottom)
                        assertTrue(row.compoundDrawablesRelative[2] != null)
                    }
                    assertEquals(rows[0].left, rows[1].left)
                    assertEquals(rows[0].right, rows[2].right)
                    val home = inflate(R.layout.activity_customer_home)
                    val title = home.findViewById<TextView>(R.id.txtAiTitle)
                    val arrow = home.findViewById<View>(R.id.icArrowAi)
                    assertTrue("$code $width $scale heading overlaps arrow", title.right <= arrow.left)
                    assertTrue(title.layout.height <= title.height - title.compoundPaddingTop - title.compoundPaddingBottom)
                    val driver = inflate(R.layout.activity_driver_settings)
                    assertTrue(driver.findViewById<View>(R.id.driverSettingsLanguage).measuredHeight > 0)
                    val driverHome = inflate(R.layout.activity_driver_home)
                    val badge = driverHome.findViewById<View>(R.id.layoutVerified)
                    val photo = driverHome.findViewById<View>(R.id.profileImageContainer)
                    assertTrue("$code $width $scale badge overlaps photo", badge.right <= photo.left)
                }
            }
        }
    }

    @Test fun localeContextPreservesFontScaleAndDoesNotMutateSharedConfiguration() {
        val original = Configuration(context.resources.configuration)
        val processLocale = Locale.getDefault()
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val key = "Locale.Helper.Selected.Language"
        val saved = prefs.getString(key, null)
        try {
            for (code in listOf("en", "tl", "ilo")) {
                val localized = LocaleHelper.setLocale(context, code)
                assertEquals(code, localized.resources.configuration.locales[0].language)
                assertEquals(original.fontScale, localized.resources.configuration.fontScale)
                assertEquals(original.densityDpi, localized.resources.configuration.densityDpi)
                assertEquals(original.locales, context.resources.configuration.locales)
                assertEquals(processLocale, Locale.getDefault())
                assertEquals(code, LocaleHelper.getLanguage(context))
            }
        } finally {
            prefs.edit().apply { if (saved == null) remove(key) else putString(key, saved) }.commit()
        }
    }
}
