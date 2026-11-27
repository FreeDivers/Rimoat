package org.qinyan.rimoat.data.update

import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun version(value: String) = checkNotNull(ReleaseVersion.parse(value))

    @Test fun versionsCompareNumericSegmentsAndPrereleases() {
        assertTrue(version("v1.10.0") > version("1.9.9"))
        assertEquals(0, version("1.2").compareTo(version("v1.2.0+42")))
        assertTrue(version("1.2.0") > version("1.2.0-rc.9"))
        assertTrue(version("1.2.0-rc.10") > version("1.2.0-rc.2"))
        assertTrue(version("1.2.0-beta") > version("1.2.0-9"))
        assertTrue(version("1.2.0") < version("2.0.0"))
        assertNull(ReleaseVersion.parse("latest"))
    }

    @Test fun releaseNotesKeepChosenSectionVerbatimAndFollowChineseLanguageVariants() {
        val release = AppRelease("v1.2.0", "### 简体中文\r\n\r\n- 新增更新检查\r\n- 修复遥控问题\r\n### English\r\n\r\n- Check for updates\r\n- Fix remote controls", null)
        listOf("zh-Hans", "zh-CN", "zh-Hant", "zh-TW", "zh-HK").forEach {
            assertEquals("- 新增更新检查\r\n- 修复遥控问题", release.notes(it))
        }
        listOf("en", "ja", "de").forEach {
            assertEquals("- Check for updates\r\n- Fix remote controls", release.notes(it))
        }
        val englishOnly = release.copy(body = "### English\nEnglish only")
        listOf("zh-Hans", "zh-Hant", "zh-TW", "zh-CN").forEach {
            assertEquals("English only", englishOnly.notes(it))
        }
        val chineseOnly = release.copy(body = "### 简体中文\n仅中文说明")
        listOf("en", "ja").forEach {
            assertEquals("仅中文说明", chineseOnly.notes(it))
        }
        assertEquals("Legacy release notes", release.copy(body = "Legacy release notes").notes("en"))
        assertEquals("Legacy release notes", release.copy(body = "Legacy release notes").notes("zh-Hans"))
    }

    @Test fun parserSelectsPublishedRepositoryApkAndRejectsInvalidReleases() {
        val release = parseRelease("""{"tag_name":"v1.2.0","body":"Notes","assets":[
            {"name":"Rimoat-v1.2.0.apk","size":42,"browser_download_url":"https://github.com/FreeDivers/Rimoat/releases/download/v1.2.0/Rimoat-v1.2.0.apk"},
            {"name":"source.zip","size":3,"browser_download_url":"https://github.com/FreeDivers/Rimoat/archive.zip"}]}""")
        assertEquals("Rimoat-v1.2.0.apk", release.asset?.name)
        assertNull(parseRelease("""{"tag_name":"v1.2.0","assets":[{"name":"app.apk","size":42,"browser_download_url":"https://example.com/app.apk"}]}""").asset)
        for (body in listOf("not json", """{"tag_name":"latest"}""", """{"tag_name":"v1.2.0","prerelease":true}""")) {
            assertEquals(UpdateFailure.INVALID_RELEASE, assertThrows(UpdateException::class.java) { parseRelease(body) }.failure)
        }
    }
}
