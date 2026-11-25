package org.qinyan.rimoat.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.system.ErrnoException
import android.system.OsConstants
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal interface UpdateRepository {
    val installedVersion: String
    suspend fun latestRelease(): AppRelease
    suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File
}

internal class AppUpdateRepository(
    context: Context,
    private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) : UpdateRepository {
    private val app = context.applicationContext
    private val packages = app.packageManager
    private val installed = packageInfo()
    override val installedVersion = installed.versionName.orEmpty()

    override suspend fun latestRelease(): AppRelease {
        var cleaned = false
        return withMirrors(LATEST_RELEASE, isApi = true, connectTimeoutMs = 8_000, readTimeoutMs = 8_000) { connection, active ->
            if (!cleaned) {
                removeExpiredDownloads()
                cleaned = true
            }
            checkResponse(connection, downloading = false)
            val body = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (active()) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > 2 * 1024 * 1024) throw UpdateException(UpdateFailure.INVALID_RELEASE)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            parseRelease(body)
        }
    }

    override suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File {
        val asset = release.asset ?: throw UpdateException(UpdateFailure.NO_APK)
        return withMirrors(
            asset.browser_download_url,
            isApi = false,
            connectTimeoutMs = 10_000,
            readTimeoutMs = 30_000,
            onRetry = { progress(0L, asset.size) },
        ) { connection, active ->
            checkResponse(connection, downloading = true)
            val directory = File(app.cacheDir, "updates")
            if (!directory.isDirectory && !directory.mkdirs()) throw UpdateException(UpdateFailure.STORAGE)
            if (directory.usableSpace < asset.size + 1024 * 1024) throw UpdateException(UpdateFailure.STORAGE)
            // A unique staging file prevents a cancelled request from removing a newer download.
            val file = File.createTempFile("release-", ".apk", directory)
            var completed = false
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (active()) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            received += count
                            if (received > asset.size) throw UpdateException(UpdateFailure.INVALID_APK)
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            progress(received, asset.size)
                        }
                    }
                }
                if (!active()) throw CancellationException()
                if (received != asset.size) throw UpdateException(UpdateFailure.DOWNLOAD)
                asset.digest?.let { expected ->
                    val actual = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
                    if (!expected.equals(actual, ignoreCase = true)) throw UpdateException(UpdateFailure.INVALID_APK)
                }
                validateApk(file, release)
                if (!active()) throw CancellationException()
                completed = true
                file
            } finally {
                if (!completed) file.delete()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(): PackageInfo = packages.getPackageInfo(app.packageName, signatureFlags())

    @Suppress("DEPRECATION")
    private fun signatureFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, release: AppRelease) {
        val archive = packages.getPackageArchiveInfo(file.absolutePath, signatureFlags())
            ?: throw UpdateException(UpdateFailure.INVALID_APK)
        if (archive.packageName != app.packageName ||
            !matchingVersion(archive.versionName.orEmpty(), release.tag) ||
            PackageInfoCompat.getLongVersionCode(archive) < PackageInfoCompat.getLongVersionCode(installed) ||
            archive.applicationInfo?.minSdkVersion?.let { it > Build.VERSION.SDK_INT } == true) {
            throw UpdateException(UpdateFailure.INVALID_APK)
        }
        val current = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners
            else installed.signatures
        val candidate = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val history = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.signingCertificateHistory else candidate
        val matches = if (current?.size == 1 && candidate?.size == 1) {
            history?.contains(current.single()) == true
        } else !current.isNullOrEmpty() && current.toSet() == candidate?.toSet()
        if (!matches) {
            throw UpdateException(UpdateFailure.SIGNATURE)
        }
    }

    private fun matchingVersion(archive: String, release: String): Boolean {
        val apk = ReleaseVersion.parse(archive) ?: return false
        val tag = ReleaseVersion.parse(release) ?: return false
        val current = ReleaseVersion.parse(installedVersion) ?: return false
        return apk.compareTo(tag) == 0 && apk > current
    }

    private fun removeExpiredDownloads() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        File(app.cacheDir, "updates").listFiles()?.filter {
            it.name.startsWith("release-") && it.extension == "apk" && it.lastModified() < cutoff
        }?.forEach { it.delete() }
    }

    private fun checkResponse(connection: HttpURLConnection, downloading: Boolean) {
        when (connection.responseCode) {
            200 -> Unit
            403, 429 -> throw UpdateException(UpdateFailure.RATE_LIMIT)
            404 -> throw UpdateException(if (downloading) UpdateFailure.NO_APK else UpdateFailure.NO_RELEASE)
            else -> throw UpdateException(if (downloading) UpdateFailure.DOWNLOAD else UpdateFailure.NETWORK)
        }
    }

    /** Silently falls back through configured proxies when GitHub is unavailable, surfacing the primary error if all fail. */
    private suspend fun <T> withMirrors(
        officialUrl: String,
        isApi: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        onRetry: () -> Unit = {},
        block: (HttpURLConnection, () -> Boolean) -> T,
    ): T {
        val candidates = listOf(officialUrl) + MIRROR_PREFIXES.map { it + officialUrl }
        var primaryError: Exception? = null
        var allTimedOut = true
        for ((index, url) in candidates.withIndex()) {
            if (index > 0) onRetry()
            try {
                return request(url, isApi, connectTimeoutMs, readTimeoutMs, block)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error !is SocketTimeoutException) allTimedOut = false
                if ((error as? UpdateException)?.failure == UpdateFailure.STORAGE) throw error
                if (primaryError == null) primaryError = error
            }
        }
        val failure = primaryError ?: UpdateException(UpdateFailure.NETWORK)
        if (failure is SocketTimeoutException && !allTimedOut) throw UpdateException(UpdateFailure.NETWORK)
        throw failure
    }

    /** Cancellation completes immediately and closes the blocking network operation off the UI thread. */
    private suspend fun <T> request(
        url: String,
        isApi: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        block: (HttpURLConnection, () -> Boolean) -> T,
    ): T =
        suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpURLConnection?>()
            val future = requests.submit {
                try {
                    val client = openConnection(url).apply {
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        setRequestProperty("Accept", if (isApi) "application/vnd.github+json" else "application/octet-stream")
                        setRequestProperty("User-Agent", "Rimoat")
                        if (isApi) setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    }
                    connection.set(client)
                    if (!continuation.isActive) return@submit
                    val result = block(client) { continuation.isActive && !Thread.currentThread().isInterrupted }
                    if (continuation.isActive) continuation.resume(result) { _, value, _ ->
                        if (value is File) value.delete()
                    }
                    else if (result is File) result.delete()
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(when {
                        error is UpdateException -> error
                        error is SocketTimeoutException -> error
                        error is IOException && generateSequence<Throwable>(error) { it.cause }
                            .any { it is ErrnoException && it.errno == OsConstants.ENOSPC } -> UpdateException(UpdateFailure.STORAGE)
                        else -> UpdateException(UpdateFailure.NETWORK)
                    })
                } finally {
                    connection.get()?.disconnect()
                }
            }
            continuation.invokeOnCancellation {
                future.cancel(true)
                requests.execute { connection.get()?.disconnect() }
            }
        }

    companion object {
        private const val LATEST_RELEASE = "https://api.github.com/repos/FreeDivers/Rimoat/releases/latest"
        private val MIRROR_PREFIXES = listOf("https://gh-proxy.com/", "https://gh-proxy.org/")
        private val requests = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "AppUpdate").apply { isDaemon = true }
        }
    }
}
