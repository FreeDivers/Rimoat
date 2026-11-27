package org.qinyan.rimoat.i18n

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.IrRemoteApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element

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
    fun selectedChannelStatusUsesTheLocaleFormatWithoutMiddleDots() {
        for ((tag, expected) in mapOf(
            "en" to "Selected: USB",
            "zh-CN" to "已选：USB",
            "zh-Hant" to "已選：USB",
            "ja" to "選択中・USB"
        )) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            val actual = context.getString(R.string.control_selected_status, "USB")
            assertEquals(tag, expected, actual)
            assertTrue("$tag does not use U+00B7", '\u00b7' !in actual)
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
    @Config(sdk = [35])
    fun everySupportedLanguageHasMatchingResourceKeysAndArgumentTypes() {
        val root = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory } ?: error("Packaged Android resources were not found")
        val baseline = readResourceEntries(File(root, "values"))
        val requiredKeys = baseline.filterValues { it.translatable }.keys
        for (directory in listOf("values", "values-en", "values-zh", "values-b+zh+Hant", "values-ja")) {
            val entries = readResourceEntries(File(root, directory))
            assertEquals("$directory missing resources", emptySet<String>(), requiredKeys - entries.keys)
            assertEquals("$directory extra resources", emptySet<String>(), entries.keys - baseline.keys)
            for ((key, entry) in entries) {
                val original = baseline.getValue(key)
                if (key.startsWith("plurals/")) {
                    val requiredForms = if (directory in setOf("values", "values-en")) setOf("one", "other") else setOf("other")
                    assertTrue("$directory $key plural forms", entry.values.keys.containsAll(requiredForms))
                }
                if (directory == "values-en") assertEquals("$directory $key default English", original, entry)
                if (!original.formatted) continue
                val expected = argumentTypes(original.values["value"] ?: original.values.getValue("other"))
                for ((quantity, value) in entry.values) {
                    assertEquals("$directory $key/$quantity arguments", expected, argumentTypes(value))
                }
            }
        }
    }

    private data class ResourceEntry(val values: Map<String, String>, val translatable: Boolean, val formatted: Boolean)

    private fun readResourceEntries(directory: File): Map<String, ResourceEntry> {
        val entries = mutableMapOf<String, ResourceEntry>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        directory.listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
            val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
                if (element.tagName !in setOf("string", "plurals")) continue
                val key = element.tagName + "/" + element.getAttribute("name")
                val values = if (element.tagName == "string") mapOf("value" to element.textContent) else {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).associate { itemIndex ->
                        val item = items.item(itemIndex) as Element
                        item.getAttribute("quantity") to item.textContent
                    }.also { assertEquals("$key duplicate plural forms", items.length, it.size) }
                }
                assertNull("Duplicate resource $key", entries.put(key, ResourceEntry(values,
                    element.getAttribute("translatable") != "false", element.getAttribute("formatted") != "false")))
            }
        }
        return entries
    }

    private fun argumentTypes(value: String): Set<Pair<Int?, String>> {
        val format = Regex("""%(?:(\d+)\$)?([-#+ 0,(<]*)(?:\d+)?(?:\.\d+)?([tT][a-zA-Z]|[a-zA-Z%])""")
        var implicit = 0
        var previous: Int? = null
        return buildSet {
            for (match in format.findAll(value)) {
                val (index, flags, conversion) = match.destructured
                if (conversion == "%" || conversion == "n") continue
                val argument = when {
                    '<' in flags -> previous
                    index.isNotEmpty() -> index.toInt()
                    else -> ++implicit
                }
                add(argument to conversion.lowercase(java.util.Locale.ROOT))
                previous = argument
            }
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
    fun displayTemplatesPreserveTheirExistingSpacingInEveryLanguage() {
        for (tag in listOf("zh-CN", "zh-Hant", "en", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            assertEquals("2.0  /  MIT", context.getString(R.string.licenses_version_license, "2.0", "MIT"))
            assertEquals("Living room  Example", context.getString(R.string.screen_remote_room_brand, "Living room", "Example"))
            assertEquals("TV  Example  Model", context.getString(R.string.screen_save_summary, "TV", "Example", "Model"))
            assertEquals("24°  Cool", context.getString(R.string.widget_temperature_mode, 24, "Cool"))
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
