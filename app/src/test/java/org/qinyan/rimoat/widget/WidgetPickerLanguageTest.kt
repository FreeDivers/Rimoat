package org.qinyan.rimoat.widget

import android.app.Application
import android.app.LocaleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAppWidgetManager
import org.xmlpull.v1.XmlPullParser

/** Robolectric does not implement provider metadata updates. Parse the real manifest and XML. */
@Implements(AppWidgetManager::class)
class PickerMetadataShadow : ShadowAppWidgetManager() {
    val updates = mutableListOf<ComponentName>()
    val visibleCounts = mutableListOf<Int>()
    var failComponent: ComponentName? = null
    var omitHiddenProviders = false

    private val app get() = RuntimeEnvironment.getApplication()

    fun installProviders() {
        WidgetPickerPreviews.entries.forEach { entry ->
            addInstalledProvider(readInfo(ComponentName(app, entry.receiver), AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        }
    }

    fun readInfo(component: ComponentName, key: String): AppWidgetProviderInfo {
        val receiver = app.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
        val resource = receiver.metaData.getInt(key)
        check(resource != 0) { "Missing provider metadata $key for $component" }
        return app.resources.getXml(resource).use { parser ->
            while (parser.eventType != XmlPullParser.START_TAG) parser.next()
            val namespace = "http://schemas.android.com/apk/res/android"
            AppWidgetProviderInfo().apply {
                provider = component
                label = receiver.loadLabel(app.packageManager).toString()
                widgetFeatures = parser.getAttributeIntValue(namespace, "widgetFeatures", 0)
                previewImage = parser.getAttributeResourceValue(namespace, "previewImage", 0)
                previewLayout = parser.getAttributeResourceValue(namespace, "previewLayout", 0)
            }
        }
    }

    @Implementation
    override fun getInstalledProviders(): List<AppWidgetProviderInfo> = super.getInstalledProviders().filter {
        !omitHiddenProviders || it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
    }

    @Implementation(minSdk = 28)
    protected fun updateAppWidgetProviderInfo(component: ComponentName, metadataKey: String?) {
        if (component == failComponent) throw IllegalArgumentException("Provider metadata temporarily unavailable")
        val current = super.getInstalledProviders().first { it.provider == component }
        val replacement = readInfo(component, metadataKey ?: AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)
        current.widgetFeatures = replacement.widgetFeatures
        current.previewImage = replacement.previewImage
        current.previewLayout = replacement.previewLayout
        updates += component
        visibleCounts += super.getInstalledProviders().count {
            it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], qualifiers = "en-rUS", application = Application::class,
    shadows = [PickerMetadataShadow::class])
class WidgetPickerLanguageTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private val shadow get() = Shadow.extract<PickerMetadataShadow>(manager)

    @Before
    fun installProviders() { shadow.installProviders() }

    private fun select(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            app.getSharedPreferences("rimoat_language", Context.MODE_PRIVATE)
                .edit().putString("language_tag", tag).commit()
        }
        WidgetPickerPreviews.synchronize(app)
    }

    private fun visible() = manager.installedProviders.filter {
        it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
    }

    private fun assertLanguage(language: String) {
        val expected = WidgetPickerPreviews.entries.filter { it.language == language }
        assertEquals(expected.map { ComponentName(app, it.receiver) }.toSet(), visible().map { it.provider }.toSet())
        assertEquals(3, visible().size)
        visible().forEach { actual ->
            val info = shadow.readInfo(actual.provider, "org.qinyan.rimoat.widget.visible")
            assertEquals(info.label, actual.label)
            assertEquals(info.previewImage, actual.previewImage)
            assertEquals(info.previewLayout, actual.previewLayout)
            assertTrue(actual.previewImage != 0 && actual.previewLayout != 0)
        }
    }

    @Test
    fun repeatedLanguageChangesKeepThreeEntriesAndSelectMatchingPreviews() {
        listOf("en", "ja", "zh-CN", "zh-Hant", "en", "ja", "").forEach { tag ->
            select(tag)
            assertLanguage(tag.ifEmpty { "en" })
        }
        assertTrue(shadow.visibleCounts.all { it >= 3 })
    }

    @Test
    fun publishingFailureKeepsOldEntriesAndNextSynchronizationRecovers() {
        select("en")
        val old = visible().map { it.provider }.toSet()
        shadow.failComponent = ComponentName(app, JaRemotePanelWidgetReceiver::class.java)
        select("ja")
        assertTrue(visible().map { it.provider }.containsAll(old))
        assertTrue(shadow.visibleCounts.all { it >= 3 })
        shadow.failComponent = null
        WidgetPickerPreviews.synchronize(app)
        assertLanguage("ja")
    }

    @Test
    fun omittedHiddenProvidersCanBeRepublishedWithoutLosingExistingBindings() {
        val existing = manager.installedProviders.first()
        shadow.bindAppWidgetId(17, existing.provider)
        shadow.omitHiddenProviders = true
        select("zh-Hant")
        assertLanguage("zh-Hant")
        assertEquals(existing.provider, manager.getAppWidgetInfo(17).provider)
        assertTrue(shadow.visibleCounts.all { it >= 3 })
    }

    @Test
    fun allHiddenProvidersRecoverToACompletePicker() {
        manager.installedProviders.forEach {
            it.widgetFeatures = it.widgetFeatures or AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER
        }
        shadow.omitHiddenProviders = true
        select("ja")
        assertLanguage("ja")
    }
}
