package org.qinyan.rimoat.data.update

import java.math.BigInteger
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class ReleaseVersion(val numbers: List<BigInteger>, val prerelease: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for (index in 0 until maxOf(numbers.size, other.numbers.size)) {
            val difference = (numbers.getOrNull(index) ?: BigInteger.ZERO)
                .compareTo(other.numbers.getOrNull(index) ?: BigInteger.ZERO)
            if (difference != 0) return difference
        }
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) return if (prerelease.isEmpty()) 1 else -1
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[index]
            val right = other.prerelease[index]
            val a = left.toBigIntegerOrNull()
            val b = right.toBigIntegerOrNull()
            val difference = when {
                a != null && b != null -> a.compareTo(b)
                a != null -> -1
                b != null -> 1
                else -> left.compareTo(right)
            }
            if (difference != 0) return difference
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(value.trim()) ?: return null
            return ReleaseVersion(match.groupValues[1].split('.').map(::BigInteger),
                match.groupValues[2].takeIf(String::isNotEmpty)?.split('.') ?: emptyList())
        }
    }
}

@Serializable
internal data class ReleaseAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long,
    val digest: String? = null,
)

internal data class AppRelease(val tag: String, val body: String, val asset: ReleaseAsset?) {
    fun notes(languageTag: String): String {
        val heading = if (Locale.forLanguageTag(languageTag).language == "zh") "简体中文" else "English"
        val sections = Regex("(?m)^###\\s+(简体中文|English)\\s*$").findAll(body).toList()
        if (sections.isEmpty()) return body.trim()
        val index = sections.indexOfFirst { it.groupValues[1] == heading }.takeIf { it >= 0 } ?: 0
        return body.substring(sections[index].range.last + 1,
            sections.getOrNull(index + 1)?.range?.first ?: body.length).trim()
    }
}

internal enum class UpdateFailure {
    CHECK_TIMEOUT, NETWORK, RATE_LIMIT, NO_RELEASE, INVALID_RELEASE, NO_APK,
    DOWNLOAD, STORAGE, INVALID_APK, SIGNATURE, PERMISSION, INSTALLER,
}

internal class UpdateException(val failure: UpdateFailure) : Exception(failure.name)

@Serializable
private data class ReleaseResponse(
    val tag_name: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

internal fun parseRelease(json: String): AppRelease {
    val response = try {
        Json { ignoreUnknownKeys = true }.decodeFromString<ReleaseResponse>(json)
    } catch (_: Exception) { throw UpdateException(UpdateFailure.INVALID_RELEASE) }
    if (response.draft || response.prerelease || ReleaseVersion.parse(response.tag_name) == null) {
        throw UpdateException(UpdateFailure.INVALID_RELEASE)
    }
    val apks = response.assets.filter {
        it.name.endsWith(".apk", ignoreCase = true) && it.size > 0 &&
            it.browser_download_url.startsWith("https://github.com/FreeDivers/Rimoat/releases/download/")
    }
    val asset = apks.firstOrNull { it.name.equals("Rimoat-${response.tag_name}.apk", ignoreCase = true) }
        ?: apks.singleOrNull()
    return AppRelease(response.tag_name, response.body.orEmpty(), asset)
}
