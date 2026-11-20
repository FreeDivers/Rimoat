package org.qinyan.rimoat.hardware.learning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manifest-registered broadcast receiver for USB device attachment and permission broadcasts.
 */
class UsbDeviceReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UsbDeviceReceiver"
        var onDeviceEvent: (() -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "USB broadcast received: $action")
        onDeviceEvent?.invoke()
    }
}
