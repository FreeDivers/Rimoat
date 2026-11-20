package org.qinyan.rimoat.i18n

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], qualifiers = "en-rUS", application = Application::class)
class AppLanguageResourcesTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun select(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            app.getSharedPreferences("rimoat_language", Context.MODE_PRIVATE)
                .edit().putString("language_tag", tag).commit()
        }
    }

    @Test
    fun backgroundAndPreviouslyLocalizedContextsFollowTheCurrentChoice() {
        select("ja")
        val japanese = AppLanguage.wrap(app)
        assertEquals("設定", japanese.getString(R.string.settings_title))
        select("zh-Hant")
        assertEquals("設定", AppLanguage.wrap(japanese).getString(R.string.settings_title))
        assertEquals("zh-Hant", AppLanguage.wrap(japanese).resources.configuration.locales[0].toLanguageTag())
        select("en")
        assertEquals("Settings", AppLanguage.wrap(japanese).getString(R.string.settings_title))
        assertEquals("Settings", AppLanguage.wrap(app).getString(R.string.settings_title))
    }

    @Test
    fun followingSystemDiscardsThePreviousOverrideImmediately() {
        select("ja")
        val previous = AppLanguage.wrap(app)
        select("")
        assertEquals("", AppLanguage.selectedTag(app))
        assertEquals("Settings", AppLanguage.wrap(previous).getString(R.string.settings_title))
        assertEquals("Settings", AppLanguage.wrap(app).getString(R.string.settings_title))
    }

    @Test
    fun wrappingPreservesDisplayAndFontConfiguration() {
        select("ja")
        val base = AppLanguage.wrap(app)
        val configuration = Configuration(base.resources.configuration).apply {
            densityDpi = 320
            fontScale = 1.6f
            screenWidthDp = 320
            uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        val resized = app.createConfigurationContext(configuration)
        val wrapped = AppLanguage.wrap(resized)
        assertNotSame(base, wrapped)
        assertEquals(320, wrapped.resources.configuration.densityDpi)
        assertEquals(1.6f, wrapped.resources.configuration.fontScale)
        assertEquals(320, wrapped.resources.configuration.screenWidthDp)
        assertEquals(Configuration.UI_MODE_NIGHT_YES,
            wrapped.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    }

    @Test
    @Config(sdk = [35])
    fun migrationKeepsPlatformChoiceAndTransfersLegacyChoiceOnlyOnce() {
        val preferences = app.getSharedPreferences("rimoat_language", Context.MODE_PRIVATE)
        preferences.edit().putString("language_tag", "ja").commit()
        AppLanguage.initialize(app)
        assertEquals("ja", AppLanguage.selectedTag(app))
        select("")
        AppLanguage.initialize(app)
        assertEquals("", AppLanguage.selectedTag(app))

        preferences.edit().remove("migrated_to_platform").commit()
        select("zh-Hant")
        AppLanguage.initialize(app)
        assertEquals("zh-Hant", AppLanguage.selectedTag(app))
    }
}
