package org.qinyan.rimoat.data.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.provider.Settings
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class, shadows = [ShadowFileProvider::class])
class AppUpdateRepositoryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val directory get() = File(app.cacheDir, "updates")
    private val release = AppRelease("v1.2.0", "", ReleaseAsset("app.apk",
        "https://github.com/FreeDivers/Rimoat/releases/download/v1.2.0/app.apk", 4))
    @After fun cleanup() { directory.listFiles()?.forEach { it.delete() } }

    private class Connection(val status: Int = 200, val stream: () -> InputStream) : HttpURLConnection(URL("https://example.com")) {
        val disconnected = CountDownLatch(1)
        override fun connect() = Unit
        override fun disconnect() { disconnected.countDown() }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = stream()
    }

    @Test fun apiRateLimitIsReportedWithoutReadingBody() = runBlocking {
        val repo = AppUpdateRepository(app) { Connection(403) { error("Must not read error response as release") } }
        try { repo.latestRelease(); fail("Expected error") }
        catch (error: UpdateException) { assertEquals(UpdateFailure.RATE_LIMIT, error.failure) }
    }

    @Test fun interruptedAndCorruptDownloadsRemoveTheirPartialFiles() = runBlocking {
        for ((bytes, digest, failure) in listOf(
            Triple(byteArrayOf(1, 2), null, UpdateFailure.DOWNLOAD),
            Triple(byteArrayOf(1, 2, 3, 4), "sha256:invalid", UpdateFailure.INVALID_APK),
            Triple(byteArrayOf(1, 2, 3, 4, 5), null, UpdateFailure.INVALID_APK),
        )) {
            val repo = AppUpdateRepository(app) { Connection { ByteArrayInputStream(bytes) } }
            try { repo.download(release.copy(asset = release.asset!!.copy(digest = digest))) { _, _ -> }; fail("Expected error") }
            catch (error: UpdateException) { assertEquals(failure, error.failure) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun cancellationClosesConnectionAndDeletesPartialDownload() = runBlocking {
        val started = CountDownLatch(1)
        val connection = Connection {
            object : InputStream() {
                override fun read(): Int {
                    started.countDown()
                    CountDownLatch(1).await(10, TimeUnit.SECONDS)
                    return -1
                }
            }
        }
        val repo = AppUpdateRepository(app) { connection }
        val job = launch(Dispatchers.Default) { repo.download(release) { _, _ -> } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(connection.disconnected.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { while (directory.listFiles().orEmpty().isNotEmpty()) delay(10) }
    }

    @Test @Suppress("DEPRECATION")
    fun downloadedApkMustMatchPackageVersionAndSignatureBeforeItCanBeShared() = runBlocking {
        val pm = Shadows.shadowOf(app.packageManager)
        val installed = pm.getInternalMutablePackageInfo(app.packageName)
        installed.versionName = "1.1.0"
        installed.versionCode = 2
        installed.signatures = arrayOf(Signature("0102"))
        var signature = "0304"
        val repo = AppUpdateRepository(app) { Connection {
            val archive = PackageInfo().apply {
                packageName = app.packageName
                versionName = "1.2"
                versionCode = 2
                signatures = arrayOf(Signature(signature))
            }
            pm.setPackageArchiveInfo(directory.listFiles()!!.single().absolutePath, archive)
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        } }
        try { repo.download(release) { _, _ -> }; fail("Expected signature rejection") }
        catch (error: UpdateException) { assertEquals(UpdateFailure.SIGNATURE, error.failure) }
        signature = "0102"
        val progress = mutableListOf<Long>()
        val file = repo.download(release) { bytes, _ -> progress.add(bytes) }
        assertEquals(4L, file.length())
        assertEquals(listOf(4L), progress)
        val intent = UpdateInstaller.installIntent(app, file)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content", intent.data?.scheme)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun androidEightRequiresPerAppInstallPermission() {
        val pm = Shadows.shadowOf(app.packageManager)
        pm.setCanRequestPackageInstalls(false)
        assertFalse(UpdateInstaller.allowed(app))
        val intent = UpdateInstaller.permissionIntent(app)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${app.packageName}", intent.data.toString())
        pm.setCanRequestPackageInstalls(true)
        assertTrue(UpdateInstaller.allowed(app))
    }

    @Test @Config(sdk = [24])
    fun androidSevenDoesNotRequestUnknownSourcePermission() {
        assertTrue(UpdateInstaller.allowed(app))
    }

    @Test fun expiredCacheIsRemovedButRecentInstallerFilesArePreserved() = runBlocking {
        directory.mkdirs()
        val expired = File(directory, "release-expired.apk").apply {
            writeText("old")
            setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        }
        val recent = File(directory, "release-recent.apk").apply { writeText("recent") }
        val repo = AppUpdateRepository(app) { Connection { ByteArrayInputStream("""{"tag_name":"v1.2.0"}""".toByteArray()) } }
        repo.latestRelease()
        assertFalse(expired.exists())
        assertTrue(recent.exists())
    }

    @Test fun latestReleaseSilentlyFallsBackThroughProxiesWhenGithubIsUnavailable() = runBlocking {
        val official = "https://api.github.com/repos/FreeDivers/Rimoat/releases/latest"
        val requested = mutableListOf<String>()
        val headers = mutableMapOf<String, String?>()
        val repo = AppUpdateRepository(app) { url ->
            requested += url
            when (url) {
                official -> throw SocketTimeoutException("blocked")
                "https://gh-proxy.com/$official" -> Connection(502) { ByteArrayInputStream(ByteArray(0)) }
                "https://gh-proxy.org/$official" -> object : HttpURLConnection(URL(url)) {
                    override fun connect() = Unit
                    override fun disconnect() = Unit
                    override fun usingProxy() = false
                    override fun getResponseCode() = 200
                    override fun setRequestProperty(key: String?, value: String?) {
                        if (key != null) headers[key] = value
                    }
                    override fun getInputStream() = ByteArrayInputStream("""{"tag_name":"v1.2.0"}""".toByteArray())
                }
                else -> error("Unexpected url $url")
            }
        }
        assertEquals("v1.2.0", repo.latestRelease().tag)
        assertEquals(
            listOf(official, "https://gh-proxy.com/$official", "https://gh-proxy.org/$official"),
            requested,
        )
        assertEquals("application/vnd.github+json", headers["Accept"])

        requested.clear()
        val firstMirrorRepo = AppUpdateRepository(app) { url ->
            requested += url
            when (url) {
                official -> Connection(403) { ByteArrayInputStream(ByteArray(0)) }
                "https://gh-proxy.com/$official" -> Connection { ByteArrayInputStream("""{"tag_name":"v1.3.0"}""".toByteArray()) }
                else -> error("Should not fall back further once gh-proxy.com succeeds: $url")
            }
        }
        assertEquals("v1.3.0", firstMirrorRepo.latestRelease().tag)
        assertEquals(listOf(official, "https://gh-proxy.com/$official"), requested)
    }

    @Test fun latestReleaseReportsGithubErrorWhenAllSourcesFail() = runBlocking {
        val official = "https://api.github.com/repos/FreeDivers/Rimoat/releases/latest"
        val rateLimited = AppUpdateRepository(app) { url ->
            when (url) {
                official -> Connection(403) { ByteArrayInputStream(ByteArray(0)) }
                "https://gh-proxy.com/$official" -> throw IOException("unreachable")
                else -> Connection(502) { ByteArrayInputStream(ByteArray(0)) }
            }
        }
        try { rateLimited.latestRelease(); fail("Expected GitHub rate limit error") }
        catch (error: UpdateException) { assertEquals(UpdateFailure.RATE_LIMIT, error.failure) }

        val partialTimeout = AppUpdateRepository(app) { url ->
            if (url == official) throw SocketTimeoutException("official timeout")
            throw IOException("proxy unreachable")
        }
        try { partialTimeout.latestRelease(); fail("Expected network error") }
        catch (error: UpdateException) { assertEquals(UpdateFailure.NETWORK, error.failure) }

        val allTimeout = AppUpdateRepository(app) { throw SocketTimeoutException("timeout") }
        try { allTimeout.latestRelease(); fail("Expected timeout") }
        catch (_: SocketTimeoutException) {}
    }

    @Test @Suppress("DEPRECATION")
    fun downloadSilentlyFallsBackToProxiesAndVerifiesApk() = runBlocking {
        val pm = Shadows.shadowOf(app.packageManager)
        val installed = pm.getInternalMutablePackageInfo(app.packageName)
        installed.versionName = "1.1.0"
        installed.versionCode = 2
        installed.signatures = arrayOf(Signature("0102"))
        val official = release.asset!!.browser_download_url
        val requested = mutableListOf<String>()
        val repo = AppUpdateRepository(app) { url ->
            requested += url
            when (url) {
                official -> throw IOException("official blocked")
                "https://gh-proxy.com/$official" -> Connection { ByteArrayInputStream(byteArrayOf(1, 2)) }
                "https://gh-proxy.org/$official" -> Connection {
                    val archive = PackageInfo().apply {
                        packageName = app.packageName
                        versionName = "1.2.0"
                        versionCode = 3
                        signatures = arrayOf(Signature("0102"))
                    }
                    pm.setPackageArchiveInfo(directory.listFiles()!!.single().absolutePath, archive)
                    ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
                }
                else -> error("Unexpected url $url")
            }
        }
        val progress = mutableListOf<Long>()
        val file = repo.download(release) { bytes, _ -> progress.add(bytes) }
        assertTrue(file.isFile)
        assertEquals(4L, file.length())
        assertEquals(
            listOf(official, "https://gh-proxy.com/$official", "https://gh-proxy.org/$official"),
            requested,
        )
        assertEquals(listOf(0L, 2L, 0L, 4L), progress)
        assertEquals(1, directory.listFiles().orEmpty().size)
    }
}
