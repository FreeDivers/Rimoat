package org.qinyan.rimoat.ui.viewmodel

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.qinyan.rimoat.data.RemoteButton

/** Preserve navigation and completed test observations, never a pending transmission or confirmation action. */
@Serializable
internal data class PairingProgress(
    val categoryId: Int? = null,
    val brandId: Int? = null,
    val step: AddRemoteStep = AddRemoteStep.CATEGORY,
    val query: String = "",
    val brands: Map<String, BrandProgress> = emptyMap(),
    val version: Int = 2
)

@Serializable
internal data class BrandProgress(
    val remoteId: String? = null,
    val name: String = "",
    /** Read old drafts without losing their previously confirmed observations. */
    val verified: Map<String, Map<String, String>> = emptyMap(),
    val observations: Map<String, Map<String, PairingObservation>> = emptyMap()
)

@Serializable
enum class PairingResult { UNTESTED, VALID, INVALID }

@Serializable
data class PairingObservation(
    val fingerprint: String,
    val result: PairingResult = PairingResult.UNTESTED,
    val sent: Boolean = false,
    val simulated: Boolean = false
)

/** Only compare complete, independent static signals for the same unambiguous function. */
internal fun distinguishingPairingKeys(current: List<RemoteButton>, other: List<RemoteButton>): Set<String> {
    // Negative catalog IDs also encode shapes/colours, so never infer a function from them.
    // Require the original function label too; aliases with no reviewed mapping are not guessed.
    fun identity(button: RemoteButton) = "${button.sourceKeyType?.takeIf { it >= 0 }}:${button.label}"
    val currentGroups = current.filter { it.hasSignal && it.acAction == null }.groupBy(::identity)
    val otherGroups = other.filter { it.hasSignal && it.acAction == null }.groupBy(::identity)
    return currentGroups.mapNotNull { (key, buttons) ->
        val ours = buttons.singleOrNull() ?: return@mapNotNull null
        val theirs = otherGroups[key]?.singleOrNull() ?: return@mapNotNull null
        val equal = ours.signalType == theirs.signalType && ours.frequencyHz == theirs.frequencyHz &&
            ours.rawPattern == theirs.rawPattern && ours.necAddress == theirs.necAddress &&
            ours.necCommand == theirs.necCommand && ours.necExtended == theirs.necExtended &&
            ours.alternateRawPattern == theirs.alternateRawPattern &&
            ours.alternateFrequencyHz == theirs.alternateFrequencyHz
        ours.id.takeUnless { equal }
    }.toSet()
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

internal fun pairingFingerprint(button: RemoteButton): String {
    val digest = java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(Json.encodeToString(button).toByteArray(Charsets.UTF_8))
    val chars = CharArray(16)
    for (i in 0 until 8) {
        val byte = digest[i].toInt() and 0xFF
        chars[i * 2] = HEX_DIGITS[byte ushr 4]
        chars[i * 2 + 1] = HEX_DIGITS[byte and 0x0F]
    }
    return String(chars)
}
