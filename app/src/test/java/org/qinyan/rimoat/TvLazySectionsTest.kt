package org.qinyan.rimoat

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.screens.tvRemoteItems
import org.junit.Assert.assertEquals
import org.junit.Test

class TvLazySectionsTest {
    @Test fun everySectionIsRegisteredBeforeCompositionAndNeverFromAnItemBody() {
        val keys = mutableListOf<Any?>()
        val list = object : LazyListScope {
            override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
                keys += key
            }
            override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?,
                itemContent: @Composable LazyItemScope.(Int) -> Unit) {
                repeat(count) { keys += key?.invoke(it) }
            }
        }
        list.tvRemoteItems(RemoteProfile(id = "tv", name = "电视"), onSendButton = {})
        // Register each section before composition so remeasurement keeps lazy-list keys unique.
        assertEquals(listOf("tv_section_0", "tv_section_1", "tv_section_2", "tv_section_3", "tv_numpad"), keys)
    }
}
