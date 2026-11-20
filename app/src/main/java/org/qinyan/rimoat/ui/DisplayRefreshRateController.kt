package org.qinyan.rimoat.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import androidx.core.content.ContextCompat

internal data class DisplayModeRate(val width: Int, val height: Int, val refreshRate: Float, val modeId: Int = 0)

internal fun preferredDisplayMode(
    width: Int,
    height: Int,
    modes: List<DisplayModeRate>,
    powerSave: Boolean
): DisplayModeRate? = if (powerSave) null else modes.asSequence()
    .filter { it.width == width && it.height == height && it.refreshRate.isFinite() && it.refreshRate > 0f }
    .maxByOrNull { it.refreshRate }

internal fun preferredRefreshRate(
    width: Int,
    height: Int,
    modes: List<DisplayModeRate>,
    powerSave: Boolean
): Float = preferredDisplayMode(width, height, modes, powerSave)?.refreshRate ?: 0f

/** Same-resolution foreground requests; the system still owns display and power policy. */
internal class DisplayRefreshRateController(private val activity: Activity) : DisplayManager.DisplayListener {
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private val power = activity.getSystemService(PowerManager::class.java)
    private var active = false
    private var composeView: View? = null
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = update()
    }

    fun bind(view: View?) {
        if (Build.VERSION.SDK_INT >= 35) composeView?.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        composeView = view
        update()
    }

    fun start() {
        if (active) return
        active = true
        displays?.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(activity, powerReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        update()
    }

    fun stop() {
        if (!active) return
        active = false
        displays?.unregisterDisplayListener(this)
        activity.unregisterReceiver(powerReceiver)
        applyMode(null)
    }

    fun update() {
        if (!active) return
        val display = composeView?.display ?: activity.window.decorView.display ?: return
        val mode = display.mode
        applyMode(preferredDisplayMode(mode.physicalWidth, mode.physicalHeight,
            display.supportedModes.map { DisplayModeRate(it.physicalWidth, it.physicalHeight, it.refreshRate, it.modeId) },
            power?.isPowerSaveMode == true))
    }

    private fun applyMode(mode: DisplayModeRate?) {
        val rate = mode?.refreshRate ?: 0f
        val modeId = mode?.modeId ?: 0
        val attributes = activity.window.attributes
        if (attributes.preferredRefreshRate != rate || attributes.preferredDisplayModeId != modeId) {
            attributes.preferredRefreshRate = rate
            // Displays with multiple refresh rates may require a matching display-mode request
            // in addition to the rate/View votes, while retaining the current resolution.
            // Both preferences are cleared when paused or when power saving is enabled.
            attributes.preferredDisplayModeId = modeId
            activity.window.attributes = attributes
        }
        // ARR can otherwise give a Compose host View a normal (often 60 Hz) vote.
        if (Build.VERSION.SDK_INT >= 35) composeView?.let { view ->
            val requested = if (rate > 0f) rate else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            if (view.requestedFrameRate != requested) view.requestedFrameRate = requested
        }
    }

    override fun onDisplayAdded(displayId: Int) = update()
    override fun onDisplayRemoved(displayId: Int) = update()
    override fun onDisplayChanged(displayId: Int) {
        if (displayId == (composeView?.display ?: activity.window.decorView.display)?.displayId) update()
    }
}
