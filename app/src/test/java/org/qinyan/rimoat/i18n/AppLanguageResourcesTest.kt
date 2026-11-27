package org.qinyan.rimoat.i18n

import org.qinyan.rimoat.IrRemoteApplication
import org.junit.Assert.assertSame
import kotlinx.coroutines.cancel
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
    fun resourceReadsReuseTheLocaleContextUntilInvalidated() {
        select("ja")
        val trackedContext = object : android.content.ContextWrapper(app) {
            var serviceLookups = 0
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                serviceLookups++
                return super.getSystemService(name)
            }
        }
        AppStrings.initialize(trackedContext)
        val japanese = AppStrings.resourceContext()
        val initialLookups = trackedContext.serviceLookups
        repeat(20) {
            assertEquals("設定", AppStrings.get(R.string.settings_title))
            assertSame(japanese, AppStrings.resourceContext())
        }
        assertEquals(initialLookups, trackedContext.serviceLookups)
        select("en")
        AppLanguage.invalidate()
        assertEquals("Settings", AppStrings.get(R.string.settings_title))
        assertNotSame(japanese, AppStrings.resourceContext())
    }

    @Test
    @Config(sdk = [32])
    fun settingTheLanguageInvalidatesPreviouslyResolvedStrings() {
        select("ja")
        AppStrings.initialize(app)
        assertEquals("設定", AppStrings.get(R.string.settings_title))
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        AppLanguage.set(activity, "en")
        assertEquals("Settings", AppStrings.get(R.string.settings_title))
        activity.finish()
    }

    @Test
    @Config(sdk = [35])
    fun applicationConfigurationCallbackRefreshesLocalesAndFontScale() {
        select("ja,en")
        AppStrings.initialize(app)
        val previous = AppStrings.resourceContext()
        val originalFontScale = app.resources.configuration.fontScale
        val application = IrRemoteApplication()
        try {
            select("zh-Hant,en")
            RuntimeEnvironment.setFontScale(1.6f)
            application.onConfigurationChanged(Configuration(app.resources.configuration))
            val refreshed = AppStrings.resourceContext()
            assertNotSame(previous, refreshed)
            assertEquals("zh-Hant", refreshed.resources.configuration.locales[0].toLanguageTag())
            assertEquals("en", refreshed.resources.configuration.locales[1].toLanguageTag())
            assertEquals(1.6f, refreshed.resources.configuration.fontScale, 0.001f)
            assertEquals(refreshed.getString(R.string.settings_title), AppStrings.get(R.string.settings_title))
        } finally {
            RuntimeEnvironment.setFontScale(originalFontScale)
            application.applicationScope.cancel()
            AppLanguage.invalidate()
        }
    }

    @Test
    fun resourceArgumentsResolveInAllSupportedLanguages() {
        for (tag in listOf("zh-CN", "zh-Hant", "en", "ja")) {
            select(tag)
            AppStrings.initialize(app)
            val context = AppStrings.resourceContext()
            assertEquals(context.getString(R.string.control_learned_button_name, "Custom"),
                AppStrings.get(R.string.control_learned_button_name, "Custom"))
            assertEquals(context.resources.getQuantityString(R.plurals.control_minutes, 3, 3),
                AppStrings.quantity(R.plurals.control_minutes, 3, 3))
            val name = UiText.resource(R.string.screen_default_remote_name,
                UiText.CatalogBrand("品牌", "Brand"), UiText.Resource(R.string.screen_category_tv))
            assertEquals(context.getString(R.string.screen_default_remote_name,
                if (tag.startsWith("zh")) "品牌" else "Brand", context.getString(R.string.screen_category_tv)),
                name.resolve(context))
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
