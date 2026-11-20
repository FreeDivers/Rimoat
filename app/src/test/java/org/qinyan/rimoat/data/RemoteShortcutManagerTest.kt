package org.qinyan.rimoat.data

import org.junit.Assert.*
import org.junit.Test

class RemoteShortcutManagerTest {
    private fun remote(id: String, favorite: Boolean = false, used: Long = 0) =
        RemoteProfile(id, "Remote $id", isFavorite = favorite, lastUsedAt = used, updatedAt = 1)

    @Test fun favoritesThenRecent_fillCapacity_andStableTies() {
        val profiles = listOf(remote("a"), remote("b", used = 90), remote("c", favorite = true), remote("d"))
        assertEquals(listOf("c", "b", "a"), planRemoteShortcuts(profiles, emptyList(), 3).dynamic.map { it.id })
        assertEquals(listOf("c", "b", "a", "d"), planRemoteShortcuts(profiles, emptyList(), 8).dynamic.map { it.id })
        assertTrue(planRemoteShortcuts(profiles, emptyList(), -1).dynamic.isEmpty())
    }

    @Test fun deletion_invalidatesOnlyOwnedTargets_andKeepsOtherLauncherActions() {
        val states = listOf(RemoteShortcutState("remote:a", dynamic = true, pinned = true),
            RemoteShortcutState("remote:b", pinned = true), RemoteShortcutState("other", dynamic = true, pinned = true))
        val plan = planRemoteShortcuts(listOf(remote("b")), states, 4)
        assertEquals(listOf("remote:a"), plan.removeDynamic)
        assertEquals(listOf("remote:a"), plan.disable)
        assertEquals(listOf("b"), plan.pinned.map { it.id })
    }

    @Test fun restore_reenablesPinnedTarget_andRenameUsesCurrentName() {
        val states = listOf(RemoteShortcutState("remote:a", pinned = true, enabled = false))
        val plan = planRemoteShortcuts(listOf(remote("a").copy(name = "New name")), states, 1)
        assertEquals(listOf("remote:a"), plan.enable)
        assertEquals("New name", plan.dynamic.single().name)
        assertEquals("New name", plan.pinned.single().name)
        assertTrue(plan.disable.isEmpty())
    }

    @Test fun rankingChange_removesDynamicEntry_butKeepsItsPinEnabled() {
        val states = listOf(RemoteShortcutState("remote:a", dynamic = true, pinned = true))
        val plan = planRemoteShortcuts(listOf(remote("a"), remote("b", favorite = true)), states, 1)
        assertEquals(listOf("remote:a"), plan.removeDynamic)
        assertEquals(listOf("a"), plan.pinned.map { it.id })
        assertTrue(plan.disable.isEmpty())
    }

    @Test fun invalidAndDuplicateIds_doNotConsumeExtraSlots() {
        val profiles = listOf(remote(""), remote("a"), remote("a"), remote("b"))
        assertEquals(listOf("a", "b"), planRemoteShortcuts(profiles, emptyList(), 4).dynamic.map { it.id })
    }
}
