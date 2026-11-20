package org.qinyan.rimoat.data

import kotlinx.serialization.Serializable

/** Presentation only. Signal data and the original default button order are never rewritten. */
@Serializable
data class RemotePanelLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet()
) {
    fun rank(id: String): Int = order.indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
