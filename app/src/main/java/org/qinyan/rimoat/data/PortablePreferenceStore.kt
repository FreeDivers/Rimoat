package org.qinyan.rimoat.data

import android.content.Context

/** Explicit allow-list. Never enumerate or copy Android or hardware preferences. */
internal object PortablePreferenceStore {
    private fun prefs(context: Context) = context.getSharedPreferences("rimoat_theme_prefs", Context.MODE_PRIVATE)
    fun read(context: Context) = PortablePreferences(
        prefs(context).getBoolean("use_dynamic_color", false),
        prefs(context).getBoolean("pure_black_background", false))
    fun write(context: Context, values: PortablePreferences) {
        val editor = prefs(context).edit()
        values.dynamicColors?.let { editor.putBoolean("use_dynamic_color", it) }
        values.pureBlack?.let { editor.putBoolean("pure_black_background", it) }
        check(editor.commit()) { org.qinyan.rimoat.i18n.AppStrings.get(org.qinyan.rimoat.R.string.migration_preferences_failed) }
    }
}
