package org.qinyan.rimoat.hardware

import android.os.Build

/**
 * Utility to identify hardware runtime environment (Physical device vs. Emulator/CI).
 */
object HardwareEnvironment {
    /**
     * Detects if the current environment is an Android emulator (QEMU, Google SDK, Genymotion).
     */
    fun isEmulator(): Boolean {
        return try {
            (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.startsWith("sdk_gphone")
                    || Build.PRODUCT.startsWith("sdk_gphone")
                    || Build.HARDWARE in setOf("goldfish", "ranchu")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == Build.PRODUCT)
        } catch (_: Throwable) {
            false
        }
    }
}

