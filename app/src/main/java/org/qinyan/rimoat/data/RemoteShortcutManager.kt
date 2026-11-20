package org.qinyan.rimoat.data

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import org.qinyan.rimoat.MainActivity
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppLanguage

internal const val REMOTE_SHORTCUT_PREFIX = "remote:"

internal data class RemoteShortcutState(
    val id: String,
    val dynamic: Boolean = false,
    val pinned: Boolean = false,
    val enabled: Boolean = true
)

internal data class RemoteShortcutPlan(
    val dynamic: List<RemoteProfile>,
    val pinned: List<RemoteProfile>,
    val removeDynamic: List<String>,
    val disable: List<String>,
    val enable: List<String>
)

/** Stable repository order breaks ties. Signal/state edits never change the ranking. */
internal fun planRemoteShortcuts(
    profiles: List<RemoteProfile>, existing: List<RemoteShortcutState>, capacity: Int
): RemoteShortcutPlan {
    val valid = profiles.filter { it.id.isNotBlank() }.distinctBy { it.id }
    val byShortcut = valid.associateBy { REMOTE_SHORTCUT_PREFIX + it.id }
    val selected = valid.sortedWith(compareByDescending<RemoteProfile> { it.isFavorite }
        .thenByDescending { it.lastUsedAt }).take(capacity.coerceAtLeast(0))
    val selectedIds = selected.mapTo(mutableSetOf()) { REMOTE_SHORTCUT_PREFIX + it.id }
    val owned = existing.filter { it.id.startsWith(REMOTE_SHORTCUT_PREFIX) }
    return RemoteShortcutPlan(
        dynamic = selected,
        pinned = owned.filter { it.pinned }.mapNotNull { byShortcut[it.id] },
        removeDynamic = owned.filter { it.dynamic && it.id !in selectedIds }.map { it.id },
        disable = owned.filter { it.enabled && it.id !in byShortcut }.map { it.id },
        enable = owned.filter { !it.enabled && it.id in byShortcut }.map { it.id }
    )
}

/** Publishes panel navigation only. Call with a fully loaded, committed repository snapshot. */
class RemoteShortcutManager(context: Context) {
    private val appContext = context.applicationContext

    @Synchronized
    fun sync(profiles: List<RemoteProfile>): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT < 25) return@runCatching
        val platform = appContext.getSystemService(ShortcutManager::class.java) ?: return@runCatching
        val dynamic = platform.dynamicShortcuts
        val pinned = platform.pinnedShortcuts
        val existing = (dynamic + pinned).associateBy { it.id }
        val unrelatedDynamic = dynamic.count { !it.id.startsWith(REMOTE_SHORTCUT_PREFIX) }
        val capacity = platform.maxShortcutCountPerActivity - platform.manifestShortcuts.size - unrelatedDynamic
        val plan = planRemoteShortcuts(profiles, existing.values.map {
            RemoteShortcutState(it.id, it.isDynamic, it.isPinned, it.isEnabled)
        }, capacity)

        // Invalidation is never postponed by a publication rate limit.
        if (plan.removeDynamic.isNotEmpty()) platform.removeDynamicShortcuts(plan.removeDynamic)
        if (plan.disable.isNotEmpty()) platform.disableShortcuts(plan.disable,
            AppLanguage.wrap(appContext).getString(R.string.shortcut_remote_unavailable))
        if (plan.enable.isNotEmpty()) platform.enableShortcuts(plan.enable)

        fun shortcut(profile: RemoteProfile, rank: Int): ShortcutInfo {
            val name = profile.name.ifBlank { appContext.getString(R.string.app_name) }
            return ShortcutInfo.Builder(appContext, REMOTE_SHORTCUT_PREFIX + profile.id)
                .setShortLabel(name).setLongLabel(name).setRank(rank)
                .setIcon(Icon.createWithResource(appContext, R.drawable.ic_launcher_foreground))
                .setIntent(openRemoteIntent(appContext, profile.id)).build()
        }
        fun changed(info: ShortcutInfo): Boolean {
            val previous = existing[info.id] ?: return true
            return previous.shortLabel != info.shortLabel || previous.longLabel != info.longLabel ||
                previous.rank != info.rank || remoteIdFromIntent(previous.intent) != remoteIdFromIntent(info.intent)
        }
        val desiredDynamic = plan.dynamic.mapIndexed { rank, profile -> shortcut(profile, rank) }
        val additions = desiredDynamic.filter { changed(it) || existing[it.id]?.isDynamic != true }
        if (additions.isNotEmpty()) check(platform.addDynamicShortcuts(additions)) { "Shortcut publication rate limited" }
        val desiredIds = desiredDynamic.mapTo(mutableSetOf()) { it.id }
        val pinUpdates = plan.pinned.filter { REMOTE_SHORTCUT_PREFIX + it.id !in desiredIds }
            .map { shortcut(it, existing[REMOTE_SHORTCUT_PREFIX + it.id]?.rank ?: 0) }.filter(::changed)
        if (pinUpdates.isNotEmpty()) check(platform.updateShortcuts(pinUpdates)) { "Pinned shortcut update rate limited" }
    }

    companion object {
        const val ACTION_OPEN_REMOTE = "org.qinyan.rimoat.action.OPEN_REMOTE"
        const val EXTRA_REMOTE_ID = "shortcut_remote_id"

        fun openRemoteIntent(context: Context, remoteId: String): Intent =
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_REMOTE)
                .setData(Uri.Builder().scheme("rimoat").authority("remote").appendPath(remoteId).build())
                .putExtra(EXTRA_REMOTE_ID, remoteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** The destination must still be resolved against the loaded repository by MainActivity. */
        fun remoteIdFromIntent(intent: Intent?): String? =
            if (intent?.action == ACTION_OPEN_REMOTE) intent.getStringExtra(EXTRA_REMOTE_ID)?.takeIf { it.isNotBlank() }
            else null
    }
}
