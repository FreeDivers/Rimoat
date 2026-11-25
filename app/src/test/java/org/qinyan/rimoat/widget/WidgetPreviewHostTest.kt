package org.qinyan.rimoat.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowViewGroup
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], qualifiers = "en-mdpi", application = Application::class,
    shadows = [WidgetPreviewHostTest.FrameworkWidgetHostShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetPreviewHostTest {
    // Exercise framework asynchronous inflation instead of the simplified host shadow.
    @Implements(AppWidgetHostView::class)
    class FrameworkWidgetHostShadow : ShadowViewGroup()

    @Test fun boundProviderRendersAsyncConfigurationPreview() = runBlocking {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_IrRemote)
        controller.setup()
        try {
            val activity = controller.get()
            val provider = ComponentName(activity, RemotePanelWidgetReceiver::class.java)
            val info = AppWidgetProviderInfo().apply { this.provider = provider }
            ReflectionHelpers.setField(info, "providerInfo", ActivityInfo().apply {
                packageName = activity.packageName
                name = provider.className
                applicationInfo = activity.applicationInfo
            })
            val pending = ArrayDeque<Runnable>()
            val host = createConfigurationPreviewHost(activity, 7, info, pending::addLast)
            assertEquals(7, host.appWidgetId)
            assertSame(info, host.appWidgetInfo)
            assertNotNull(LayoutInflater.from(activity).factory2)
            assertNull(LayoutInflater.from(host.context).factory2)
            activity.setContentView(host)
            val profile = RemoteProfile("remote", "Preview TV", buttons = listOf(
                RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560))))
            val configuration = WidgetConfiguration(WidgetKind.PANEL, shortcuts = listOf(WidgetShortcut(profile.id, "power")))
            val snapshot = WidgetSnapshot(loaded = true, localeTags = "en", profiles = listOf(profile),
                commands = mapOf(profile.id to WidgetCommands.choices(profile)))
            val views = composeConfigurationPreview(WidgetPreviewComposer.previewContext(activity),
                configuration, snapshot, DpSize(250.dp, 160.dp))
            host.updateAppWidget(views)
            assertFalse(pending.isEmpty())
            while (pending.isNotEmpty()) pending.removeFirst().run()
            shadowOf(Looper.getMainLooper()).idle()
            host.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 250, 160)
            fun texts(view: View): List<String> = when (view) {
                is TextView -> listOf(view.text.toString())
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            assertTrue(texts(host).contains(profile.name))
            if (android.os.Build.VERSION.SDK_INT == 31) {
                host.captureRoboImage("src/test/screenshots/widget_preview_host_api31.webp",
                    roborazziOptions = RoborazziOptions(recordOptions =
                        RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())))
            }
        } finally { controller.pause().stop().destroy() }
    }

    // API 31 uses Glance's service-backed collection path; this exercises its in-memory adapter.
    @Test
    @Config(sdk = [35])
    fun overflowingPreviewScrollsWithoutLaunchingActivitiesOrSendingCommands() = runBlocking {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_IrRemote)
        controller.setup()
        try {
            val activity = controller.get()
            val provider = ComponentName(activity, RemotePanelWidgetReceiver::class.java)
            val info = AppWidgetProviderInfo().apply { this.provider = provider }
            ReflectionHelpers.setField(info, "providerInfo", ActivityInfo().apply {
                packageName = activity.packageName
                name = provider.className
                applicationInfo = activity.applicationInfo
            })
            val pending = ArrayDeque<Runnable>()
            val host = createConfigurationPreviewHost(activity, 8, info, pending::addLast)
            activity.setContentView(host)
            val profiles = (1..6).map { index ->
                RemoteProfile("remote_$index", "Remote $index", buttons = listOf(
                    RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560))))
            }
            val configuration = WidgetConfiguration(WidgetKind.PANEL,
                shortcuts = profiles.map { WidgetShortcut(it.id, "power") }, arrangement = WidgetArrangement.LIST)
            val snapshot = WidgetSnapshot(loaded = true, localeTags = "en", profiles = profiles,
                commands = profiles.associate { it.id to WidgetCommands.choices(it) })
            val views = composeConfigurationPreview(WidgetPreviewComposer.previewContext(activity),
                configuration, snapshot, DpSize(280.dp, 240.dp))
            host.updateAppWidget(views)
            assertFalse("The real host inflates the preview asynchronously", pending.isEmpty())
            while (pending.isNotEmpty()) pending.removeFirst().run()
            shadowOf(Looper.getMainLooper()).idle()
            host.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 280, 240)
            val list = descendants(host).filterIsInstance<ListView>().single()
            assertEquals("All configured rows remain in the preview adapter", profiles.size, list.count)
            assertTrue("The preview retains native list scrolling", list.canScrollVertically(1))
            assertEquals(0, list.firstVisiblePosition)
            assertFalse("The last row starts outside the viewport", visibleTexts(host).contains(profiles.last().name))
            assertPreviewActionsAreInert(host, activity)
            val imageOptions = RoborazziOptions(recordOptions =
                RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
            host.captureRoboImage("src/test/screenshots/widget_preview_scroll_before_api${android.os.Build.VERSION.SDK_INT}.webp",
                roborazziOptions = imageOptions)

            list.scrollListBy(list.height * 2)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The list moved away from the first row", list.firstVisiblePosition > 0)
            assertEquals("The final configured row can be reached", profiles.lastIndex, list.lastVisiblePosition)
            assertTrue("The final row name is visible", visibleTexts(host).contains(profiles.last().name))
            assertFalse("The preview reached the end of the list", list.canScrollVertically(1))
            val lastName = descendants(host).filterIsInstance<TextView>().single { it.text.toString() == profiles.last().name }
            val visible = Rect()
            assertTrue(lastName.getGlobalVisibleRect(visible))
            assertEquals("The final row text is fully visible", lastName.height, visible.height())
            val application = shadowOf(activity.application)
            val broadcastsBefore = application.broadcastIntents.size
            val hostLocation = IntArray(2).also(host::getLocationOnScreen)
            val labelLocation = IntArray(2).also(lastName::getLocationOnScreen)
            val x = labelLocation[0] - hostLocation[0] + lastName.width / 2f
            val y = labelLocation[1] - hostLocation[1] + lastName.height / 2f
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0)
            try {
                host.dispatchTouchEvent(down)
                host.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertNull("Preview touch never launches an activity", application.nextStartedActivity)
            assertEquals("Preview touch never broadcasts a command", broadcastsBefore, application.broadcastIntents.size)

            // Exercise newly rebound rows again without relying on touch to clear their actions.
            list.scrollListBy(-list.height * 2)
            list.scrollListBy(list.height * 2)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("The last row remains reachable after rebinding", visibleTexts(host).contains(profiles.last().name))
            host.viewTreeObserver.dispatchOnPreDraw()
            host.captureRoboImage("src/test/screenshots/widget_preview_scroll_after_api${android.os.Build.VERSION.SDK_INT}.webp",
                roborazziOptions = imageOptions)
            assertPreviewActionsAreInert(host, activity)
        } finally { controller.pause().stop().destroy() }
    }

    private fun assertPreviewActionsAreInert(host: ViewGroup, activity: AppCompatActivity) {
        val application = shadowOf(activity.application)
        val broadcastsBefore = application.broadcastIntents.size
        assertNull(application.nextStartedActivity)
        for (view in descendants(host).filter { it.getGlobalVisibleRect(Rect()) }) {
            assertFalse("Preview views do not play click sounds", view.isSoundEffectsEnabled)
            assertFalse("Preview views do not handle clicks", view.performClick())
            if (view is AdapterView<*>) {
                assertNull("Preview rows have no click action", view.onItemClickListener)
                assertNull("Preview rows have no long-click action", view.onItemLongClickListener)
                if (view.childCount > 0) {
                    val position = view.firstVisiblePosition
                    assertFalse(view.performItemClick(view.getChildAt(0), position, view.getItemIdAtPosition(position)))
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("Preview interactions never launch an activity", application.nextStartedActivity)
        assertEquals("Preview interactions never broadcast a command", broadcastsBefore, application.broadcastIntents.size)
    }

    private fun visibleTexts(root: View): List<String> = descendants(root).filterIsInstance<TextView>()
        .filter { it.getGlobalVisibleRect(Rect()) }.map { it.text.toString() }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
